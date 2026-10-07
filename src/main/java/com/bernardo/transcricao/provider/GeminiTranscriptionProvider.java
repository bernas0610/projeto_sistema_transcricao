package com.bernardo.transcricao.provider;

import com.bernardo.transcricao.exception.TranscriptionException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class GeminiTranscriptionProvider implements TranscriptionProvider {

    private static final String HEADER_CHAVE = "x-goog-api-key";
    private static final int MAX_TENTATIVAS = 3;

    private final RestClient client;
    private final String apiKey;
    private final String baseUrl;
    private final String modelo;
    private final String idioma;

    public GeminiTranscriptionProvider(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.gemini.model:gemini-3.5-transcribe}") String modelo,
            @Value("${app.gemini.idioma:pt-BR}") String idioma,
            @Value("${app.gemini.timeout-minutos:10}") long timeoutMinutos) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelo = modelo;
        this.idioma = idioma;

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
        fabrica.setReadTimeout(Duration.ofMinutes(timeoutMinutos));
        this.client = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public String transcrever(Path audio, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TranscriptionException("GEMINI_API_KEY não configurada");
        }

        FileInfo arquivo = comRetry("upload do áudio", () -> enviar(audio, mimeType));
        try {
            aguardarAtivo(arquivo);
            return comRetry("transcrição", () -> gerar(arquivo.uri(), mimeType));
        } finally {
            apagar(arquivo.name());
        }
    }

    // ---------- chamadas à API ----------

    private FileInfo enviar(Path audio, String mimeType) {
        long tamanho;
        try {
            tamanho = Files.size(audio);
        } catch (IOException e) {
            throw new TranscriptionException("Não foi possível ler o áudio: " + audio, e);
        }

        // 1) abre a sessão de upload resumable
        ResponseEntity<Void> inicio = client.post()
                .uri(URI.create(baseUrl + "/upload/v1beta/files"))
                .header(HEADER_CHAVE, apiKey)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", String.valueOf(tamanho))
                .header("X-Goog-Upload-Header-Content-Type", mimeType)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("file", Map.of("display_name", audio.getFileName().toString())))
                .retrieve()
                .toBodilessEntity();

        String urlUpload = inicio.getHeaders().getFirst("X-Goog-Upload-URL");
        if (urlUpload == null) {
            throw new TranscriptionException("O Gemini não devolveu a URL de upload");
        }

        // 2) envia os bytes e finaliza
        UploadResponse resposta = client.post()
                .uri(URI.create(urlUpload))
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(audio))
                .retrieve()
                .body(UploadResponse.class);

        if (resposta == null || resposta.file() == null || resposta.file().uri() == null) {
            throw new TranscriptionException("Resposta de upload inválida do Gemini");
        }
        return resposta.file();
    }

    private void aguardarAtivo(FileInfo inicial) {
        FileInfo atual = inicial;
        for (int i = 0; i < 15 && atual != null && "PROCESSING".equalsIgnoreCase(atual.state()); i++) {
            dormir(Duration.ofSeconds(2));
            atual = comRetry("consulta do arquivo", () -> client.get()
                    .uri(URI.create(baseUrl + "/v1beta/" + inicial.name()))
                    .header(HEADER_CHAVE, apiKey)
                    .retrieve()
                    .body(FileInfo.class));
        }
        if (atual == null || "FAILED".equalsIgnoreCase(atual.state())) {
            throw new TranscriptionException("O Gemini não conseguiu processar o arquivo enviado");
        }
    }

    private String gerar(String uriArquivo, String mimeType) {
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "input", List.of(Map.of(
                        "type", "audio",
                        "uri", uriArquivo,
                        "mime_type", mimeType)),
                "generation_config", Map.of(
                        "transcription_config", Map.of(
                                "language_codes", List.of(idioma))));

        InteractionResponse resposta = client.post()
                .uri(URI.create(baseUrl + "/v1beta/interactions"))
                .header(HEADER_CHAVE, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(InteractionResponse.class);

        return extrairTexto(resposta);
    }

    private void apagar(String nome) {
        if (nome == null) {
            return;
        }
        try {
            client.delete()
                    .uri(URI.create(baseUrl + "/v1beta/" + nome))
                    .header(HEADER_CHAVE, apiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // melhor esforço: o Gemini apaga os arquivos sozinho após 48 h
        }
    }

    // ---------- resposta ----------

    private String extrairTexto(InteractionResponse resposta) {
        StringBuilder texto = new StringBuilder();
        if (resposta != null) {
            if (resposta.steps() != null) {
                for (Step passo : resposta.steps()) {
                    if ("model_output".equals(passo.type())) {
                        acumular(texto, passo.content());
                    }
                }
            }
            if (texto.isEmpty() && resposta.outputs() != null) {
                acumular(texto, resposta.outputs());
            }
        }
        if (texto.isEmpty()) {
            throw new TranscriptionException("O Gemini não retornou texto na resposta");
        }
        return texto.toString().strip();
    }

    private void acumular(StringBuilder destino, List<ContentItem> itens) {
        if (itens == null) {
            return;
        }
        for (ContentItem item : itens) {
            if ("text".equals(item.type()) && item.text() != null && !item.text().isBlank()) {
                if (!destino.isEmpty()) {
                    destino.append("\n");
                }
                destino.append(item.text());
            }
        }
    }

    // ---------- retry e utilitários ----------

    private <T> T comRetry(String operacao, Supplier<T> acao) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return acao.get();
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean transitorio = status == 429 || status >= 500;
                if (!transitorio || tentativa >= MAX_TENTATIVAS) {
                    throw new TranscriptionException("Falha em " + operacao + " (HTTP " + status + "): "
                            + resumo(e.getResponseBodyAsString()), e);
                }
            } catch (ResourceAccessException e) {
                if (tentativa >= MAX_TENTATIVAS) {
                    throw new TranscriptionException(
                            "Falha em " + operacao + " (rede/timeout): " + e.getMessage(), e);
                }
            }
            dormir(Duration.ofSeconds(10L * tentativa));
        }
    }

    private void dormir(Duration duracao) {
        try {
            Thread.sleep(duracao);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Transcrição interrompida", e);
        }
    }

    private String resumo(String texto) {
        if (texto == null) {
            return "";
        }
        String limpo = texto.strip();
        return limpo.length() > 500 ? limpo.substring(0, 500) : limpo;
    }

    // ---------- DTOs (só os campos que usamos) ----------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadResponse(FileInfo file) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileInfo(String name, String uri, String state) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InteractionResponse(List<Step> steps, List<ContentItem> outputs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String type, List<ContentItem> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentItem(String type, String text) {
    }
}
