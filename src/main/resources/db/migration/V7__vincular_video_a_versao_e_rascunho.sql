ALTER TABLE versao ALTER COLUMN arquivo_url DROP NOT NULL;
ALTER TABLE versao ADD COLUMN video_id UUID REFERENCES video_asset(id);
ALTER TABLE versao ADD CONSTRAINT ck_versao_origem CHECK (
    (arquivo_url IS NOT NULL AND video_id IS NULL)
    OR (arquivo_url IS NULL AND video_id IS NOT NULL)
);
CREATE INDEX idx_versao_video ON versao(video_id);

ALTER TABLE video_asset ADD COLUMN projeto_rascunho_id UUID REFERENCES projeto(id);
CREATE INDEX idx_video_projeto_rascunho ON video_asset(projeto_rascunho_id);
