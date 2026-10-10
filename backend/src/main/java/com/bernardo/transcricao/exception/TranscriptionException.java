package com.bernardo.transcricao.exception;

public class TranscriptionException extends RuntimeException {
    private final CodigoErro codigo;
    public CodigoErro getCodigo() { return codigo; }
    public TranscriptionException(CodigoErro codigo) { this(codigo, null); }
    public TranscriptionException(CodigoErro codigo, Throwable causa) {
        super(codigo.mensagem(), causa);
        this.codigo = codigo;
    }

    public TranscriptionException(String mensagem) {
        super(mensagem);
        this.codigo = CodigoErro.RESPOSTA_PROVEDOR;
    }

    public TranscriptionException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.codigo = CodigoErro.RESPOSTA_PROVEDOR;
    }
}
