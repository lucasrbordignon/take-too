package com.kinkan.take_too.domain.dto;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

public record MarcaDaguaPreferenciasDTO(
        boolean ativaPadrao, @Nullable UUID logoImagemId, @Nullable ImagemDTO logo,
        MarcaDaguaDTO.Posicao posicao, int larguraPercentual, int opacidadePercentual,
        int margemPercentual, @Nullable Instant atualizadoEm
) {}
