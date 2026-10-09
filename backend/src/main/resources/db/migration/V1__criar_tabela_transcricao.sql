CREATE TABLE transcricao (
                             id                    UUID PRIMARY KEY,
                             nome_arquivo_original VARCHAR(255) NOT NULL,
                             caminho_arquivo       VARCHAR(500) NOT NULL,
                             status                VARCHAR(20)  NOT NULL,
                             texto                 TEXT,
                             mensagem_erro         TEXT,
                             criado_em             TIMESTAMP WITH TIME ZONE NOT NULL,
                             atualizado_em         TIMESTAMP WITH TIME ZONE NOT NULL
);