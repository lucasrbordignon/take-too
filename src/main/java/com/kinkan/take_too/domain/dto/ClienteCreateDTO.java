package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record ClienteCreateDTO(
        @NotBlank String nome,
        @NotBlank String telefone,
        @org.jspecify.annotations.Nullable String email
) {
}
