package com.kinkan.take_too.domain.dto;

import java.util.UUID;

public record ProfissionalDTO(
        UUID id,
        String nome,
        String email,
        String plano
) {
}
