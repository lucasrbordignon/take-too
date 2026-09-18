CREATE TABLE profissional (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    plano VARCHAR(50) NOT NULL
);

CREATE TABLE cliente (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255)
);

CREATE TABLE profissional_cliente (
    profissional_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    PRIMARY KEY (profissional_id, cliente_id),
    FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE CASCADE,
    FOREIGN KEY (cliente_id) REFERENCES cliente(id) ON DELETE CASCADE
);

CREATE TABLE projeto (
    id UUID PRIMARY KEY,
    profissional_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    nome VARCHAR(255) NOT NULL,
    etapa_atual VARCHAR(50) NOT NULL,
    FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE CASCADE,
    FOREIGN KEY (cliente_id) REFERENCES cliente(id) ON DELETE CASCADE
);

CREATE TABLE versao (
    id UUID PRIMARY KEY,
    projeto_id UUID NOT NULL,
    numero INTEGER NOT NULL,
    arquivo_url VARCHAR(1024) NOT NULL,
    status VARCHAR(50) NOT NULL,
    FOREIGN KEY (projeto_id) REFERENCES projeto(id) ON DELETE CASCADE
);

CREATE TABLE comentario (
    id UUID PRIMARY KEY,
    versao_id UUID NOT NULL,
    cliente_id UUID,
    timestamp_segundos INTEGER NOT NULL,
    texto TEXT NOT NULL,
    resolvido BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (versao_id) REFERENCES versao(id) ON DELETE CASCADE,
    FOREIGN KEY (cliente_id) REFERENCES cliente(id) ON DELETE SET NULL
);
