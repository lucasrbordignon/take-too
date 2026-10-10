package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.*;

public record MarcaDaguaDTO(
        @NotBlank @Size(max = 2048) String imagemUrl,
        @NotNull Posicao posicao,
        @Min(1) @Max(100) int larguraPercentual,
        @Min(0) @Max(100) int opacidadePercentual,
        @Min(0) @Max(40) int margemPercentual
) {
    public enum Posicao { SUPERIOR_ESQUERDA, SUPERIOR_DIREITA, INFERIOR_ESQUERDA, INFERIOR_DIREITA, CENTRO }
}
