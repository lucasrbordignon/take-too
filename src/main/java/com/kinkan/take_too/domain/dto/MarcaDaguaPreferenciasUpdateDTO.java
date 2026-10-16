package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.*;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

public record MarcaDaguaPreferenciasUpdateDTO(
        @NotNull Boolean ativaPadrao,
        @Nullable UUID logoImagemId,
        @NotNull MarcaDaguaDTO.Posicao posicao,
        @NotNull @Min(1) @Max(100) Integer larguraPercentual,
        @NotNull @Min(1) @Max(100) Integer opacidadePercentual,
        @NotNull @Min(0) @Max(40) Integer margemPercentual
) {}
