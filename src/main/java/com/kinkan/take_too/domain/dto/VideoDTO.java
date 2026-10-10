package com.kinkan.take_too.domain.dto;

import com.kinkan.take_too.domain.enums.StatusVideo;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record VideoDTO(
        UUID id, String nome, StatusVideo status,
        @Nullable Double duracaoSegundos,
        @Nullable Integer largura, @Nullable Integer altura,
        @Nullable String proporcao, @Nullable String orientacao,
        @Nullable MarcaDaguaDTO marcaDagua, @Nullable String erro,
        Instant criadoEm, Instant atualizadoEm
) {}
