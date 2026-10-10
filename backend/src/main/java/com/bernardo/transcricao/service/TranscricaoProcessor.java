package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.StatusTranscricao;
import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.provider.TranscriptionProvider;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscricaoProcessor {

    private static final String MIME_PARTES = "audio/mp3";

    private final TranscricaoRepository repository;
    private final AudioService audioService;
    private final TranscriptionProvider provider;
    private final TranscricaoCheckpointService checkpoints;

    @Async("transcricaoExecutor")
    public void processar(UUID id) {
        Transcricao transcricao = repository.findById(id).orElse(null);
        if (transcricao == null) {
            log.warn("Transcrição {} não encontrada; nada a processar", id);
            return;
        }
        if (transcricao.getStatus() != StatusTranscricao.PENDENTE
                && transcricao.getStatus() != StatusTranscricao.PROCESSANDO) {
            return;
        }

        Path pastaPartes = null;

        try {
            Path original = Path.of(transcricao.getCaminhoArquivo());
            pastaPartes = original.resolveSibling("partes-" + id);
            if (!Files.isRegularFile(original)) {
                throw new IOException("Arquivo de áudio original não encontrado: " + original);
            }
            transcricao.setStatus(StatusTranscricao.PROCESSANDO);
            transcricao.setMensagemErro(null);
            transcricao.setTexto(null);
            if (transcricao.getDuracaoParteSegundos() == null) {
                transcricao.setDuracaoParteSegundos(audioService.getDuracaoParteSegundos());
            }
            repository.save(transcricao);

            // Remove segmentos deixados por uma execução interrompida antes de regenerá-los.
            apagarPasta(pastaPartes);
            log.info("Transcrição {}: dividindo o áudio", id);
            List<Path> partes = audioService.dividir(original, pastaPartes,
                    transcricao.getDuracaoParteSegundos());
            Map<Integer, String> textosSalvos = new HashMap<>();
            for (var salva : checkpoints.listar(id)) {
                if (salva.getNumero() >= partes.size()) {
                    throw new IOException("As partes geradas não correspondem ao progresso salvo");
                }
                textosSalvos.put(salva.getNumero(), salva.getTexto());
            }
            if (!textosSalvos.isEmpty() && transcricao.getTotalPartes() != partes.size()) {
                throw new IOException("A divisão do áudio mudou desde o progresso salvo");
            }
            transcricao.setTotalPartes(partes.size());
            transcricao.setPartesConcluidas(textosSalvos.size());
            repository.save(transcricao);

            StringBuilder texto = new StringBuilder();
            for (int i = 0; i < partes.size(); i++) {
                log.info("Transcrição {}: parte {}/{}", id, i + 1, partes.size());
                String parte = textosSalvos.get(i);
                if (parte == null) {
                    parte = provider.transcrever(partes.get(i), MIME_PARTES);
                    transcricao.setPartesConcluidas(checkpoints.salvar(id, i, parte));
                } else {
                    log.info("Transcrição {}: parte {}/{} recuperada do banco", id, i + 1, partes.size());
                }
                if (!texto.isEmpty()) {
                    texto.append("\n\n");
                }
                texto.append(parte);
            }

            transcricao.setTexto(texto.toString());
            transcricao.setStatus(StatusTranscricao.CONCLUIDA);
            repository.save(transcricao);
            apagarArquivo(original);
            log.info("Transcrição {}: concluída", id);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                // Mantém o job e o original para recuperação na próxima inicialização.
                log.info("Transcrição {} interrompida; será recuperada no próximo início", id);
                return;
            }
            log.error("Transcrição {} falhou", id, e);
            transcricao.setStatus(StatusTranscricao.ERRO);
            transcricao.setMensagemErro(mensagemDe(e));
            repository.save(transcricao);
        } finally {
            apagarPasta(pastaPartes);
        }
    }

    private String mensagemDe(Exception e) {
        String mensagem = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return mensagem.length() > 1000 ? mensagem.substring(0, 1000) : mensagem;
    }

    private void apagarArquivo(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("Não foi possível apagar {}", arquivo, e);
        }
    }

    private void apagarPasta(Path pasta) {
        if (pasta == null || !Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(this::apagarArquivo);
        } catch (IOException e) {
            log.warn("Não foi possível limpar {}", pasta, e);
        }
    }
}
