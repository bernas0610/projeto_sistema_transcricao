package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.StatusTranscricao;
import com.bernardo.transcricao.exception.CodigoErro;
import com.bernardo.transcricao.exception.TranscriptionException;
import org.slf4j.MDC;
import java.time.Instant;
import java.time.Duration;
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
        long inicio = System.nanoTime();
        String mdcAnterior = MDC.get("job_id");
        MDC.put("job_id", id.toString());
        long filaMs = transcricao.getAtualizadoEm() == null ? 0
                : Math.max(0, Duration.between(transcricao.getAtualizadoEm(), Instant.now()).toMillis());
        log.info("evento=job_inicio job_id={} fila_ms={} recuperacao={}", id, filaMs,
                transcricao.getStatus() == StatusTranscricao.PROCESSANDO);

        try {
            Path original = Path.of(transcricao.getCaminhoArquivo());
            pastaPartes = original.resolveSibling("partes-" + id);
            if (!Files.isRegularFile(original)) {
                throw new TranscriptionException(CodigoErro.ORIGINAL_AUSENTE);
            }
            transcricao.setStatus(StatusTranscricao.PROCESSANDO);
            transcricao.setMensagemErro(null);
            transcricao.setCodigoErro(null);
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
                    throw new TranscriptionException(CodigoErro.CHECKPOINT_INCOMPATIVEL);
                }
                textosSalvos.put(salva.getNumero(), salva.getTexto());
            }
            if (!textosSalvos.isEmpty() && transcricao.getTotalPartes() != partes.size()) {
                throw new TranscriptionException(CodigoErro.CHECKPOINT_INCOMPATIVEL);
            }
            transcricao.setTotalPartes(partes.size());
            transcricao.setPartesConcluidas(textosSalvos.size());
            repository.save(transcricao);

            log.info("evento=job_partes job_id={} total={} salvas={}", id, partes.size(), textosSalvos.size());
            StringBuilder texto = new StringBuilder();
            for (int i = 0; i < partes.size(); i++) {
                long inicioParte = System.nanoTime();
                MDC.put("parte", String.valueOf(i + 1));
                log.info("evento=parte_inicio job_id={} parte={} total={}", id, i + 1, partes.size());
                String parte = textosSalvos.get(i);
                if (parte == null) {
                    parte = provider.transcrever(partes.get(i), MIME_PARTES);
                    transcricao.setPartesConcluidas(checkpoints.salvar(id, i, parte));
                } else {
                    log.info("Transcrição {}: parte {}/{} recuperada do banco", id, i + 1, partes.size());
                }
                log.info("evento=parte_fim job_id={} parte={} reutilizada={} duracao_ms={}",
                        id, i + 1, textosSalvos.containsKey(i), (System.nanoTime() - inicioParte) / 1_000_000);
                MDC.remove("parte");
                if (!texto.isEmpty()) {
                    texto.append("\n\n");
                }
                texto.append(parte);
            }

            transcricao.setTexto(texto.toString());
            transcricao.setStatus(StatusTranscricao.CONCLUIDA);
            repository.save(transcricao);
            apagarArquivo(original);
            log.info("evento=job_fim job_id={} status=CONCLUIDA partes={} duracao_ms={}",
                    id, partes.size(), (System.nanoTime() - inicio) / 1_000_000);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                // Mantém o job e o original para recuperação na próxima inicialização.
                log.info("evento=job_interrompido job_id={} duracao_ms={}", id, (System.nanoTime() - inicio) / 1_000_000);
                return;
            }
            CodigoErro codigo = CodigoErro.de(e);
            log.error("evento=job_fim job_id={} status=ERRO codigo={} tipo={} duracao_ms={}",
                    id, codigo, e.getClass().getSimpleName(), (System.nanoTime() - inicio) / 1_000_000);
            transcricao.setStatus(StatusTranscricao.ERRO);
            transcricao.setCodigoErro(codigo);
            transcricao.setMensagemErro(codigo.mensagem());
            repository.save(transcricao);
        } finally {
            apagarPasta(pastaPartes);
            MDC.remove("parte");
            if (mdcAnterior == null) MDC.remove("job_id"); else MDC.put("job_id", mdcAnterior);
        }
    }

    private void apagarArquivo(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("evento=limpeza_falha job_id={} tipo={}", MDC.get("job_id"), e.getClass().getSimpleName());
        }
    }

    private void apagarPasta(Path pasta) {
        if (pasta == null || !Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> arquivos = Files.walk(pasta)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(this::apagarArquivo);
        } catch (IOException e) {
            log.warn("evento=limpeza_falha job_id={} tipo={}", MDC.get("job_id"), e.getClass().getSimpleName());
        }
    }
}
