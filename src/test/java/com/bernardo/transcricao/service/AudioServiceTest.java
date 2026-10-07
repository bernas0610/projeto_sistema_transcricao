package com.bernardo.transcricao.service;

import com.bernardo.transcricao.exception.AudioProcessingException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AudioServiceTest {

    @TempDir
    Path tmp;

    @BeforeAll
    static void ffmpegDisponivel() {
        assumeTrue(executa("ffmpeg", "-nostdin", "-version"), "ffmpeg não encontrado no PATH");
    }

    @Test
    void divideAudioEmPartes() throws Exception {
        Path entrada = tmp.resolve("entrada.wav");
        assertTrue(executa("ffmpeg", "-nostdin", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=10", entrada.toString()));

        AudioService service = new AudioService("ffmpeg", 3, 2);
        List<Path> partes = service.dividir(entrada, tmp.resolve("partes"));

        assertEquals(4, partes.size());
        for (Path parte : partes) {
            assertTrue(Files.size(parte) > 0);
        }
    }

    @Test
    void rejeitaArquivoQueNaoEAudio() throws Exception {
        Path invalido = tmp.resolve("texto.mp3");
        Files.writeString(invalido, "isto não é áudio");

        AudioService service = new AudioService("ffmpeg", 3, 2);

        assertThrows(AudioProcessingException.class,
                () -> service.dividir(invalido, tmp.resolve("partes")));
    }

    private static boolean executa(String... comando) {
        try {
            Process p = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}