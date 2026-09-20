package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record ComentarioDTO(
        UUID id,
        String texto,
        Integer timestampSegundos,
        String autorTipo,
        Instant criadoEm
) {
    public ComentarioDTO(UUID id, String texto, Integer timestampSegundos, String autorTipo) {
        this(id, texto, timestampSegundos, autorTipo, Instant.now());
    }
}
