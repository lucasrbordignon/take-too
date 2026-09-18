ALTER TABLE versao ADD CONSTRAINT uq_versao_projeto_numero UNIQUE (projeto_id, numero);
