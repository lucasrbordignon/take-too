package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record ClienteDTO(
        UUID id,
        String nome,
        String telefone,
        @org.jspecify.annotations.Nullable String email,
        Instant criadoEm,
        Integer totalProjetos,
        @org.jspecify.annotations.Nullable Instant ultimaAtividadeEm
) {
    public ClienteDTO(UUID id, String nome, String telefone, String email, Instant criadoEm) {
        this(id, nome, telefone, email, criadoEm, 0, null);
    }

    public ClienteDTO(UUID id, String nome, String telefone, String email) {
        this(id, nome, telefone, email, Instant.now(), 0, null);
    }
}
