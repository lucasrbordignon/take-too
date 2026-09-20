CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    token VARCHAR(512) NOT NULL UNIQUE,
    profissional_id UUID NOT NULL,
    data_expiracao TIMESTAMP WITH TIME ZONE NOT NULL,
    revogado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_profissional FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_token ON refresh_token(token);
CREATE INDEX idx_refresh_token_profissional ON refresh_token(profissional_id);
