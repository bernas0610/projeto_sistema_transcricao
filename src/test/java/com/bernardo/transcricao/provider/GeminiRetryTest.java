package com.bernardo.transcricao.provider;

import com.bernardo.transcricao.exception.TranscriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiRetryTest {
    private final GeminiTranscriptionProvider provider = mock(GeminiTranscriptionProvider.class, CALLS_REAL_METHODS);
    private final List<Duration> esperas = new ArrayList<>();

    {
        doAnswer(invocation -> {
            esperas.add(invocation.getArgument(0));
            return null;
        }).when(provider).dormir(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GenerateRequestsPerDayPerProjectPerModel-FreeTier",
            "GenerateContentInputTokensPerModelPerDay-FreeTier"})
    void cotaDiariaNaoRepeteMesmoComLimitePorMinutoERetryDelay(String quotaId) {
        String corpo = """
                {"error":{"message":"Quota exceeded. Please retry in 35s.","details":[
                  {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[
                    {"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"},
                    {"quotaId":"%s"}]},
                  {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"35s"}]}}
                """.formatted(quotaId);
        AtomicInteger chamadas = new AtomicInteger();

        TranscriptionException erro = assertThrows(TranscriptionException.class,
                () -> provider.comRetry("transcrição", () -> {
                    chamadas.incrementAndGet();
                    throw http(429, corpo);
                }));

        assertEquals(1, chamadas.get());
        assertTrue(esperas.isEmpty());
        assertTrue(erro.getMessage().contains("Cota diária"));
        assertInstanceOf(RestClientResponseException.class, erro.getCause());
    }

    @Test
    void identificaMetricaDiariaSemQuotaId() {
        assertTrue(GeminiQuotaError.cotaDiaria("""
                {"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
                "violations":[{"quotaMetric":"generativelanguage.googleapis.com/requests_per_day"}]}]}}
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"error\":{\"message\":\"Daily quota exceeded\"}}",
            "Daily request limit exceeded"})
    void identificaMensagemDiariaExplicita(String corpo) {
        assertTrue(GeminiQuotaError.cotaDiaria(corpo));
    }

    @Test
    void limitePorMinutoPodeTerSucessoNaTentativaSeguinte() {
        AtomicInteger chamadas = new AtomicInteger();
        String texto = provider.comRetry("transcrição", () -> {
            if (chamadas.incrementAndGet() == 1) {
                throw http(429, """
                        {"error":{"message":"Please retry in 2.5s.","details":[
                        {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[
                        {"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]}]}}
                        """);
            }
            return "Transcrição";
        });
        assertEquals("Transcrição", texto);
        assertEquals(2, chamadas.get());
        assertEquals(List.of(Duration.ofSeconds(8)), esperas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{JSON quebrado", "Quota exceeded", "{\"error\":null}",
            "{\"error\":{\"message\":\"RESOURCE_EXHAUSTED\"}}",
            "{\"error\":{\"message\":\"See docs for PerDay limits\"}}"})
    void respostaSemEvidenciaDiariaMantemCincoTentativas(String corpo) {
        AtomicInteger chamadas = new AtomicInteger();
        assertThrows(TranscriptionException.class, () -> provider.comRetry("upload", () -> {
            chamadas.incrementAndGet();
            throw http(429, corpo);
        }));
        assertEquals(5, chamadas.get());
        assertEquals(List.of(Duration.ofSeconds(30), Duration.ofSeconds(60),
                Duration.ofSeconds(90), Duration.ofSeconds(120)), esperas);
    }

    @Test
    void erro400NaoRepete() {
        AtomicInteger chamadas = new AtomicInteger();
        assertThrows(TranscriptionException.class, () -> provider.comRetry("upload", () -> {
            chamadas.incrementAndGet();
            throw http(400, "Invalid request");
        }));
        assertEquals(1, chamadas.get());
        assertTrue(esperas.isEmpty());
    }

    @Test
    void erro500MantemRetryMesmoComTextoDeCotaDiaria() {
        AtomicInteger chamadas = new AtomicInteger();
        assertEquals("ok", provider.comRetry("upload", () -> {
            if (chamadas.incrementAndGet() == 1) {
                throw http(500, "Daily quota exceeded");
            }
            return "ok";
        }));
        assertEquals(2, chamadas.get());
        assertEquals(List.of(Duration.ofSeconds(10)), esperas);
    }

    @Test
    void falhaDeRedeMantemRetry() {
        AtomicInteger chamadas = new AtomicInteger();
        assertEquals("ok", provider.comRetry("upload", () -> {
            if (chamadas.incrementAndGet() == 1) {
                throw new ResourceAccessException("timeout");
            }
            return "ok";
        }));
        assertEquals(2, chamadas.get());
        assertEquals(List.of(Duration.ofSeconds(10)), esperas);
    }

    private static RestClientResponseException http(int status, String corpo) {
        return new RestClientResponseException("Erro", status, "Erro", null,
                corpo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

}
