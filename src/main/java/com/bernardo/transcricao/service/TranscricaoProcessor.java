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

    @Async("transcricaoExecutor")
    public void processar(UUID id) {
        Transcricao transcricao = repository.findById(id).orElse(null);
        if (transcricao == null) {
            log.warn("Transcrição {} não encontrada; nada a processar", id);
            return;
        }

        Path original = Path.of(transcricao.getCaminhoArquivo());
        Path pastaPartes = original.resolveSibling("partes-" + id);

        try {
            transcricao.setStatus(StatusTranscricao.PROCESSANDO);
            repository.save(transcricao);

            log.info("Transcrição {}: dividindo o áudio", id);
            List<Path> partes = audioService.dividir(original, pastaPartes);

            StringBuilder texto = new StringBuilder();
            for (int i = 0; i < partes.size(); i++) {
                log.info("Transcrição {}: parte {}/{}", id, i + 1, partes.size());
                String parte = provider.transcrever(partes.get(i), MIME_PARTES);
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
        if (!Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(this::apagarArquivo);
        } catch (IOException e) {
            log.warn("Não foi possível limpar {}", pasta, e);
        }
    }
}