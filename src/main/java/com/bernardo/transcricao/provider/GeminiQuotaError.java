package com.bernardo.transcricao.provider;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.regex.Pattern;

/** Identifica apenas limites explicitamente diários; um 429 genérico continua temporário. */
final class GeminiQuotaError {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern MENSAGEM_DIARIA = Pattern.compile(
            "\\bdaily\\s+(?:request\\s+|token\\s+)?(?:quota|limit)\\b", Pattern.CASE_INSENSITIVE);

    private GeminiQuotaError() {
    }

    static boolean cotaDiaria(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return false;
        }
        try {
            JsonNode erro = JSON.readTree(corpo).path("error");
            for (JsonNode detalhe : erro.path("details")) {
                if (!"type.googleapis.com/google.rpc.QuotaFailure".equals(detalhe.path("@type").asString(""))) {
                    continue;
                }
                for (JsonNode violacao : detalhe.path("violations")) {
                    if (diario(violacao.path("quotaId").asString(""))
                            || diario(violacao.path("quotaMetric").asString(""))) {
                        return true;
                    }
                }
            }
            return MENSAGEM_DIARIA.matcher(erro.path("message").asString("")).find();
        } catch (JacksonException e) {
            // Respostas de proxies podem não ser JSON. Sem evidência, preserva o retry.
            return MENSAGEM_DIARIA.matcher(corpo).find();
        }
    }

    private static boolean diario(String identificador) {
        String normalizado = identificador.toLowerCase(Locale.ROOT);
        return normalizado.contains("perday") || normalizado.contains("per_day");
    }
}
