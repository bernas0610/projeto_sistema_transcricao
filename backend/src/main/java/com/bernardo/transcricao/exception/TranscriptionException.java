package com.bernardo.transcricao.exception;

public class TranscriptionException extends RuntimeException {

    public TranscriptionException(String mensagem) {
        super(mensagem);
    }

    public TranscriptionException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}