package com.bernardo.transcricao.service;

import com.bernardo.transcricao.exception.AudioProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class AudioService {

    private final String ffmpeg;
    private final int duracaoParteSegundos;
    private final long timeoutMinutos;

    public AudioService(
            @Value("${app.ffmpeg.caminho:ffmpeg}") String ffmpeg,
            @Value("${app.ffmpeg.duracao-parte-segundos:1500}") int duracaoParteSegundos,
            @Value("${app.ffmpeg.timeout-minutos:30}") long timeoutMinutos) {
        this.ffmpeg = ffmpeg;
        this.duracaoParteSegundos = duracaoParteSegundos;
        this.timeoutMinutos = timeoutMinutos;
    }

    /**
     * Converte para mono 16 kHz (mp3 32 kbps) e divide em partes de duracaoParteSegundos.
     * Retorna as partes em ordem (parte_000.mp3, parte_001.mp3, ...).
     */
    public List<Path> dividir(Path entrada, Path diretorioSaida) {
        return dividir(entrada, diretorioSaida, duracaoParteSegundos);
    }

    public int getDuracaoParteSegundos() {
        return duracaoParteSegundos;
    }

    public List<Path> dividir(Path entrada, Path diretorioSaida, int duracaoSegundos) {
        if (duracaoSegundos <= 0) {
            throw new AudioProcessingException("A duração de cada parte deve ser positiva");
        }
        try {
            Files.createDirectories(diretorioSaida);
        } catch (IOException e) {
            throw new AudioProcessingException("Não foi possível criar o diretório das partes", e);
        }

        executar(List.of(
                ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-i", entrada.toString(),
                "-vn", "-ac", "1", "-ar", "16000",
                "-c:a", "libmp3lame", "-b:a", "32k",
                "-f", "segment",
                "-segment_time", String.valueOf(duracaoSegundos),
                "-reset_timestamps", "1",
                diretorioSaida.resolve("parte_%03d.mp3").toString()
        ));

        try (Stream<Path> arquivos = Files.list(diretorioSaida)) {
            List<Path> partes = arquivos
                    .filter(p -> p.getFileName().toString().matches("parte_\\d{3}\\.mp3"))
                    .sorted()
                    .toList();
            if (partes.isEmpty()) {
                throw new AudioProcessingException("O arquivo não contém áudio utilizável");
            }
            return partes;
        } catch (IOException e) {
            throw new AudioProcessingException("Falha ao listar as partes geradas", e);
        }
    }

    private void executar(List<String> comando) {
        Path log = null;
        Process processo = null;
        try {
            log = Files.createTempFile("ffmpeg-", ".log");
            processo = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();

            if (!processo.waitFor(timeoutMinutos, TimeUnit.MINUTES)) {
                processo.destroyForcibly();
                throw new AudioProcessingException("Tempo limite excedido ao processar o áudio");
            }
            if (processo.exitValue() != 0) {
                throw new AudioProcessingException("ffmpeg falhou: " + fimDoLog(log));
            }
        } catch (IOException e) {
            throw new AudioProcessingException(
                    "Falha ao executar o ffmpeg (verifique a instalação e app.ffmpeg.caminho): "
                            + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (processo != null) {
                processo.destroyForcibly();
            }
            throw new AudioProcessingException("Processamento interrompido", e);
        } finally {
            if (log != null) {
                try {
                    Files.deleteIfExists(log);
                } catch (IOException ignored) {
                    // arquivo temporário: sem impacto
                }
            }
        }
    }

    private String fimDoLog(Path log) {
        try {
            String texto = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).strip();
            return texto.length() > 500 ? texto.substring(texto.length() - 500) : texto;
        } catch (IOException e) {
            return "(log indisponível)";
        }
    }
}
