package com.bernardo.transcricao.controller;

import com.bernardo.transcricao.dto.ErroResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> tratar(Exception erro) {
        int status = erro instanceof org.springframework.beans.TypeMismatchException ? 400
                : erro instanceof org.springframework.http.converter.HttpMessageNotReadableException ? 400
                : erro instanceof ErrorResponse resposta ? resposta.getStatusCode().value() : 500;
        if (status >= 500) log.error("evento=api_falha status={} tipo={}", status, erro.getClass().getSimpleName());
        // Não devolver motivos de exceções: podem conter entrada do usuário, SQL ou caminhos.
        return ResponseEntity.status(status).body(ErroResponse.de(status));
    }
}
