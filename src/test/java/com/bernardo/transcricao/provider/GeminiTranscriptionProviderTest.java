package com.bernardo.transcricao.provider;

import com.bernardo.transcricao.exception.TranscriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiTranscriptionProviderTest {

    private static final String PROMPT_SIMPLES =
            "Transcreva integralmente e literalmente toda a fala deste áudio, em português do Brasil. "
                    + "Não resuma e não omita nenhum trecho. Inclua as falas de todos os participantes. "
                    + "Responda apenas com a transcrição.";

    private static final String PROMPT_COM_TEMPO = PROMPT_SIMPLES
            + " Marque o tempo de cada trecho no formato [MM:SS] e identifique quem fala (Professor ou Aluno).";

    @Test
    void comparaModelosEPrompts() {
        Path audio = Path.of("teste_A.mp3");
        assumeTrue(Files.exists(audio), "Crie o teste_A.mp3 na raiz do projeto");

        Map<String, String> prompts = new LinkedHashMap<>();
        prompts.put("simples", PROMPT_SIMPLES);
        prompts.put("com tempo", PROMPT_COM_TEMPO);

        for (String modelo : List.of("gemini-3.5-flash", "gemini-3.5-flash-lite")) {
            for (Map.Entry<String, String> prompt : prompts.entrySet()) {
                String rotulo = modelo + " / " + prompt.getKey();
                try {
                    GeminiTranscriptionProvider provider = new GeminiTranscriptionProvider(
                            System.getenv("GEMINI_API_KEY"),
                            "https://generativelanguage.googleapis.com",
                            modelo,
                            "pt-BR",
                            10,
                            prompt.getValue());

                    String texto = provider.transcrever(audio, "audio/mp3");

                    System.out.println("=== " + rotulo + ": " + contarPalavras(texto) + " palavras ===");
                    System.out.println(texto.substring(0, Math.min(300, texto.length())));
                } catch (TranscriptionException e) {
                    System.out.println("=== " + rotulo + ": ERRO ===");
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    private int contarPalavras(String texto) {
        String limpo = texto
                .replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("(?i)\\b(professor|aluno|aluna|alunos)\\s*:", " ")
                .trim();
        return limpo.isEmpty() ? 0 : limpo.split("\\s+").length;
    }
}