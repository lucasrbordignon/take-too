package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record ComentarioCreateDTO(
        @NotBlank String texto,
        @org.jspecify.annotations.Nullable Integer tempoVideo
) {
}
