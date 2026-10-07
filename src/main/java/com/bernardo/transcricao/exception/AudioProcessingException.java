package com.bernardo.transcricao.exception;

public class AudioProcessingException extends RuntimeException {

    public AudioProcessingException(String mensagem) {
        super(mensagem);
    }

    public AudioProcessingException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}