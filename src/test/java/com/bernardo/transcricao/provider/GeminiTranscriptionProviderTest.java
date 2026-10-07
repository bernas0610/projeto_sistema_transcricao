package com.bernardo.transcricao.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiTranscriptionProviderTest {

    @Test
    void transcreveAudioReal() {
        Path audio = Path.of("teste.mp3");
        assumeTrue(Files.exists(audio), "Coloque um teste.mp3 na raiz do projeto");

        GeminiTranscriptionProvider provider = new GeminiTranscriptionProvider(
                System.getenv("GEMINI_API_KEY"),
                "https://generativelanguage.googleapis.com",
                "gemini-3.5-transcribe",
                "pt-BR",
                10);

        String texto = provider.transcrever(audio, "audio/mp3");

        System.out.println("TRANSCRIÇÃO: " + texto);
        assertFalse(texto.isBlank());
    }
}