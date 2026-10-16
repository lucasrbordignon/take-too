CREATE TABLE imagem_asset (
    id UUID PRIMARY KEY,
    profissional_id UUID NOT NULL REFERENCES profissional(id),
    projeto_rascunho_id UUID REFERENCES projeto(id),
    nome VARCHAR(255) NOT NULL,
    provedor VARCHAR(50) NOT NULL,
    chave VARCHAR(512) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    checksum VARCHAR(44) NOT NULL,
    tamanho_bytes BIGINT NOT NULL CHECK (tamanho_bytes > 0),
    largura INTEGER,
    altura INTEGER,
    status VARCHAR(30) NOT NULL CHECK (status IN ('AGUARDANDO_UPLOAD','PRONTA','ERRO','EXCLUIDA')),
    erro VARCHAR(512),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_imagem_chave UNIQUE (provedor, chave)
);
CREATE INDEX idx_imagem_profissional ON imagem_asset(profissional_id, criado_em);
CREATE INDEX idx_imagem_rascunho ON imagem_asset(projeto_rascunho_id);
ALTER TABLE versao ADD COLUMN imagem_id UUID REFERENCES imagem_asset(id);
ALTER TABLE versao DROP CONSTRAINT ck_versao_origem;
ALTER TABLE versao ADD CONSTRAINT ck_versao_origem CHECK (
    (arquivo_url IS NOT NULL AND video_id IS NULL AND imagem_id IS NULL)
    OR (arquivo_url IS NULL AND video_id IS NOT NULL AND imagem_id IS NULL)
    OR (arquivo_url IS NULL AND video_id IS NULL AND imagem_id IS NOT NULL)
);
CREATE INDEX idx_versao_imagem ON versao(imagem_id);
ALTER TABLE comentario ALTER COLUMN timestamp_segundos DROP NOT NULL;
