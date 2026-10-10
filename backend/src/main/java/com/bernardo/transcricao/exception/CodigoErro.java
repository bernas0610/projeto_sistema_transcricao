package com.bernardo.transcricao.exception;

/** Mensagens públicas: nunca incluir resposta bruta do provedor, caminhos ou credenciais. */
public enum CodigoErro {
    COTA_PROVEDOR_DIARIA("A cota diária do serviço de transcrição foi atingida. Aguarde sua renovação antes de reprocessar.", true),
    LIMITE_PROVEDOR("O serviço de transcrição atingiu um limite temporário. Aguarde alguns minutos antes de reprocessar.", true),
    PROVEDOR_INDISPONIVEL("O serviço de transcrição está indisponível ou demorou a responder. Tente reprocessar mais tarde.", true),
    CONFIGURACAO_PROVEDOR("O serviço de transcrição precisa de uma correção de configuração. Avise o administrador.", false),
    REQUISICAO_PROVEDOR("O serviço de transcrição recusou o pedido. Avise o administrador antes de reprocessar.", false),
    RESPOSTA_PROVEDOR("O serviço não retornou uma transcrição utilizável. Tente reprocessar mais tarde.", true),
    AUDIO_INVALIDO("Não foi possível ler o áudio. Confira o arquivo e envie uma versão válida.", false),
    PROCESSAMENTO_AUDIO("Não foi possível preparar o áudio. Avise o administrador antes de reprocessar.", false),
    ORIGINAL_AUSENTE("Arquivo de áudio original não encontrado. Envie o áudio novamente.", false),
    CHECKPOINT_INCOMPATIVEL("O áudio não corresponde às partes salvas. Avise o administrador.", false),
    ERRO_INTERNO("Não foi possível concluir o processamento. Avise o administrador antes de reprocessar.", false);

    private final String mensagem;
    private final boolean repetivel;
    CodigoErro(String mensagem, boolean repetivel) { this.mensagem = mensagem; this.repetivel = repetivel; }
    public String mensagem() { return mensagem; }
    public boolean repetivel() { return repetivel; }
    public static CodigoErro de(Throwable erro) {
        if (erro instanceof TranscriptionException e) return e.getCodigo();
        if (erro instanceof AudioProcessingException e) return e.getCodigo();
        return ERRO_INTERNO;
    }
}
