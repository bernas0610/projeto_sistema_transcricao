package com.bernardo.transcricao.exception;

public class AudioProcessingException extends RuntimeException {
    private final CodigoErro codigo;
    public CodigoErro getCodigo() { return codigo; }
    public AudioProcessingException(CodigoErro codigo) { super(codigo.mensagem()); this.codigo = codigo; }

    public AudioProcessingException(String mensagem) {
        super(mensagem);
        this.codigo = CodigoErro.PROCESSAMENTO_AUDIO;
    }

    public AudioProcessingException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.codigo = CodigoErro.PROCESSAMENTO_AUDIO;
    }
}
