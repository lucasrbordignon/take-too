package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record ClienteDTO(
        UUID id,
        String nome,
        String telefone,
        @org.jspecify.annotations.Nullable String email,
        Instant criadoEm
) {
    public ClienteDTO(UUID id, String nome, String telefone, String email) {
        this(id, nome, telefone, email, Instant.now());
    }
}
