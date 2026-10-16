CREATE TABLE profissional_marca_dagua (
    profissional_id UUID PRIMARY KEY REFERENCES profissional(id),
    logo_imagem_id UUID REFERENCES imagem_asset(id),
    ativa_padrao BOOLEAN NOT NULL DEFAULT FALSE,
    posicao VARCHAR(30) NOT NULL,
    largura_percentual INTEGER NOT NULL CHECK (largura_percentual BETWEEN 1 AND 100),
    opacidade_percentual INTEGER NOT NULL CHECK (opacidade_percentual BETWEEN 1 AND 100),
    margem_percentual INTEGER NOT NULL CHECK (margem_percentual BETWEEN 0 AND 40),
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_marca_logo_ativa CHECK (NOT ativa_padrao OR logo_imagem_id IS NOT NULL),
    CONSTRAINT ck_marca_posicao CHECK (posicao IN ('SUPERIOR_ESQUERDA','SUPERIOR_DIREITA','INFERIOR_ESQUERDA','INFERIOR_DIREITA','CENTRO'))
);
CREATE INDEX idx_marca_logo ON profissional_marca_dagua(logo_imagem_id);
ALTER TABLE video_asset ADD COLUMN logo_marca_dagua_id UUID REFERENCES imagem_asset(id);
ALTER TABLE video_asset ADD COLUMN marca_dagua_preferencias_json TEXT;
CREATE INDEX idx_video_logo_marca ON video_asset(logo_marca_dagua_id);
ALTER TABLE imagem_asset ADD COLUMN logo_marca_dagua_id UUID REFERENCES imagem_asset(id);
ALTER TABLE imagem_asset ADD COLUMN marca_dagua_preferencias_json TEXT;
CREATE INDEX idx_imagem_logo_marca ON imagem_asset(logo_marca_dagua_id);
ALTER TABLE video_asset ADD CONSTRAINT ck_video_marca_planejada CHECK (
    (logo_marca_dagua_id IS NULL AND marca_dagua_preferencias_json IS NULL)
    OR (logo_marca_dagua_id IS NOT NULL AND marca_dagua_preferencias_json IS NOT NULL)
);
ALTER TABLE imagem_asset ADD CONSTRAINT ck_imagem_marca_planejada CHECK (
    (logo_marca_dagua_id IS NULL AND marca_dagua_preferencias_json IS NULL)
    OR (logo_marca_dagua_id IS NOT NULL AND marca_dagua_preferencias_json IS NOT NULL)
);
