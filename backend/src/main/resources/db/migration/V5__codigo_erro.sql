ALTER TABLE transcricao ADD COLUMN codigo_erro VARCHAR(50);
-- Mensagens antigas podem conter respostas técnicas/caminhos; sanitizar ao migrar.
UPDATE transcricao SET codigo_erro = 'ERRO_INTERNO',
    mensagem_erro = 'Não foi possível concluir o processamento. Avise o administrador antes de reprocessar.'
    WHERE status = 'ERRO';
