package com.bernardo.transcricao.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {
    @Test void erroInternoNaoVazaDetalhes() {
        var body = new ApiExceptionHandler().tratar(new RuntimeException("SQL senha segredo caminho privado")).getBody();
        assertEquals("ERRO_INTERNO", body.code()); assertEquals(500, body.status());
        assertFalse(body.message().contains("segredo"));
    }
    @Test void cotaEConflitoMantemContrato() {
        var handler = new ApiExceptionHandler();
        var body = handler.tratar(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "detalhe interno")).getBody();
        assertEquals("COTA_UPLOAD", body.code()); assertTrue(body.retryable());
        assertEquals("CONFLITO", handler.tratar(new ResponseStatusException(HttpStatus.CONFLICT)).getBody().code());
    }
}
