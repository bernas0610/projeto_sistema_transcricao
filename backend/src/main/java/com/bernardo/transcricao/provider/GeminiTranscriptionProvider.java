package com.bernardo.transcricao.provider;

import com.bernardo.transcricao.exception.TranscriptionException;
import com.bernardo.transcricao.exception.CodigoErro;
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

import lombok.extern.slf4j.Slf4j;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
@Slf4j
public class GeminiTranscriptionProvider implements TranscriptionProvider {

    private static final String HEADER_CHAVE = "x-goog-api-key";
    private static final int MAX_TENTATIVAS = 5;
    private static final long ESPERA_MAXIMA_SEGUNDOS = 180;
    private static final Pattern RETRY_EM =
            Pattern.compile("retry in (\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE);

    private final RestClient client;
    private final String apiKey;
    private final String baseUrl;
    private final String modelo;
    private final String idioma;
    private final String prompt;

    public GeminiTranscriptionProvider(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.gemini.model:gemini-3.5-transcribe}") String modelo,
            @Value("${app.gemini.idioma:pt-BR}") String idioma,
            @Value("${app.gemini.timeout-minutos:10}") long timeoutMinutos,
            @Value("${app.gemini.prompt:}") String prompt)


    {

        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelo = modelo;
        this.idioma = idioma;
        this.prompt = prompt;

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
        fabrica.setReadTimeout(Duration.ofMinutes(timeoutMinutos));
        this.client = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public String transcrever(Path audio, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new TranscriptionException(CodigoErro.CONFIGURACAO_PROVEDOR);
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
            throw new TranscriptionException(CodigoErro.PROCESSAMENTO_AUDIO, e);
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
            throw new TranscriptionException(CodigoErro.RESPOSTA_PROVEDOR);
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
            throw new TranscriptionException(CodigoErro.RESPOSTA_PROVEDOR);
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
            throw new TranscriptionException(CodigoErro.RESPOSTA_PROVEDOR);
        }
        if ("PROCESSING".equalsIgnoreCase(atual.state())) {
            throw new TranscriptionException(CodigoErro.PROVEDOR_INDISPONIVEL);
        }
    }

    private String gerar(String uriArquivo, String mimeType) {
        Map<String, Object> audio = Map.of(
                "type", "audio",
                "uri", uriArquivo,
                "mime_type", mimeType);

        Map<String, Object> corpo;
        if (prompt == null || prompt.isBlank()) {
            // modelo dedicado de transcrição
            corpo = Map.of(
                    "model", modelo,
                    "input", List.of(audio),
                    "generation_config", Map.of(
                            "transcription_config", Map.of(
                                    "language_codes", List.of(idioma))));
        } else {
            // modelo geral: o pedido de transcrição vai como texto
            Map<String, Object> texto = Map.of("type", "text", "text", prompt);
            corpo = Map.of(
                    "model", modelo,
                    "input", List.of(texto, audio));
        }

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
            throw new TranscriptionException(CodigoErro.RESPOSTA_PROVEDOR);
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

    <T> T comRetry(String operacao, Supplier<T> acao) {
        for (int tentativa = 1; ; tentativa++) {
            Duration espera;
            try {
                long inicio = System.nanoTime();
                log.info("evento=provedor_tentativa operacao={} tentativa={}", operacao, tentativa);
                T resultado = acao.get();
                log.info("evento=provedor_sucesso operacao={} tentativa={} duracao_ms={}",
                        operacao, tentativa, (System.nanoTime() - inicio) / 1_000_000);
                return resultado;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 429 && GeminiQuotaError.cotaDiaria(e.getResponseBodyAsString())) {
                    log.warn("evento=provedor_falha operacao={} tentativa={} status=429 codigo=COTA_PROVEDOR_DIARIA", operacao, tentativa);
                    throw new TranscriptionException(CodigoErro.COTA_PROVEDOR_DIARIA, e);
                }
                boolean transitorio = status == 429 || status >= 500;
                if (!transitorio || tentativa >= MAX_TENTATIVAS) {
                    CodigoErro codigo = status == 429 ? CodigoErro.LIMITE_PROVEDOR
                            : status >= 500 ? CodigoErro.PROVEDOR_INDISPONIVEL
                            : status == 401 || status == 403 || status == 404 ? CodigoErro.CONFIGURACAO_PROVEDOR
                            : CodigoErro.REQUISICAO_PROVEDOR;
                    log.warn("evento=provedor_falha operacao={} tentativa={} status={} codigo={}", operacao, tentativa, status, codigo);
                    throw new TranscriptionException(codigo, e);
                }
                espera = esperaApos(e, tentativa);
            } catch (ResourceAccessException e) {
                if (tentativa >= MAX_TENTATIVAS) {
                    log.warn("evento=provedor_falha operacao={} tentativa={} codigo=PROVEDOR_INDISPONIVEL", operacao, tentativa);
                    throw new TranscriptionException(CodigoErro.PROVEDOR_INDISPONIVEL, e);
                }
                espera = Duration.ofSeconds(10L * tentativa);
            }
            log.warn("evento=provedor_retry operacao={} tentativa={} max_tentativas={} espera_s={}",
                    operacao, tentativa, MAX_TENTATIVAS, espera.toSeconds());
            dormir(espera);
        }
    }

    private Duration esperaApos(RestClientResponseException e, int tentativa) {
        if (e.getStatusCode().value() == 429) {
            Matcher m = RETRY_EM.matcher(e.getResponseBodyAsString());
            if (m.find()) {
                // usa o tempo que o próprio Gemini pediu, com margem de 5 s
                long segundos = (long) Math.ceil(Double.parseDouble(m.group(1))) + 5;
                return Duration.ofSeconds(Math.min(segundos, ESPERA_MAXIMA_SEGUNDOS));
            }
            return Duration.ofSeconds(Math.min(30L * tentativa, ESPERA_MAXIMA_SEGUNDOS));
        }
        return Duration.ofSeconds(10L * tentativa);
    }

    void dormir(Duration duracao) {
        try {
            Thread.sleep(duracao);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Transcrição interrompida", e);
        }
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
