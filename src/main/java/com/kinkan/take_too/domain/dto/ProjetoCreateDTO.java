package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProjetoCreateDTO(
        @NotBlank String nome,
        @NotNull UUID clienteId
) {
}
