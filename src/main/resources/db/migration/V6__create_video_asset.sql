CREATE TABLE video_asset (
    id UUID PRIMARY KEY,
    profissional_id UUID NOT NULL REFERENCES profissional(id) ON DELETE CASCADE,
    nome VARCHAR(255) NOT NULL,
    provedor VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    upload_externo_id VARCHAR(255),
    asset_externo_id VARCHAR(255),
    playback_externo_id VARCHAR(255),
    duracao_segundos DOUBLE PRECISION,
    largura INTEGER,
    altura INTEGER,
    proporcao VARCHAR(50),
    erro VARCHAR(1024),
    marca_dagua_json TEXT,
    upload_expira_em TIMESTAMP WITH TIME ZONE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_video_upload UNIQUE (provedor, upload_externo_id),
    CONSTRAINT uq_video_asset UNIQUE (provedor, asset_externo_id),
    CONSTRAINT ck_video_status CHECK (status IN (
        'CRIANDO_UPLOAD', 'AGUARDANDO_UPLOAD', 'PROCESSANDO', 'PRONTO', 'ERRO', 'CANCELADO', 'EXCLUIDO'
    ))
);
CREATE INDEX idx_video_profissional_criado ON video_asset (profissional_id, criado_em);
