CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    dia_uso DATE,
    arquivos_usados INTEGER NOT NULL DEFAULT 0
);

-- Registros anteriores permanecem sem dono e não são expostos pela API autenticada.
ALTER TABLE transcricao ADD COLUMN usuario_id UUID REFERENCES usuario(id);
CREATE INDEX idx_transcricao_usuario ON transcricao(usuario_id);
