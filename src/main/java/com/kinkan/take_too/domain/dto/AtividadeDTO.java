package com.kinkan.take_too.domain.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record AtividadeDTO(
        String id,
        String tipo,
        String titulo,
        String descricao,
        String autor,
        String autorTipo,
        Instant timestamp,
        @Nullable UUID versaoId,
        @Nullable Integer versaoNumero,
        @Nullable Integer tempoVideo
) {
}
