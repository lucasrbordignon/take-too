package com.kinkan.take_too.domain.dto;

import java.util.UUID;

public record ComentarioDTO(
        UUID id,
        String texto,
        Integer timestampSegundos,
        String autorTipo
) {
}
