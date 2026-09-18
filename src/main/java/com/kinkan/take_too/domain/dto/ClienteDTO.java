package com.kinkan.take_too.domain.dto;

import java.util.UUID;

public record ClienteDTO(
        UUID id,
        String nome,
        String telefone,
        @org.jspecify.annotations.Nullable String email
) {
}
