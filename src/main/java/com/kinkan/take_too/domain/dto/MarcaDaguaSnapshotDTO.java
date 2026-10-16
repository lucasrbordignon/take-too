package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.UUID;

/** Configuração planejada e imutável; não indica aplicação no arquivo de revisão. */
public record MarcaDaguaSnapshotDTO(
        UUID logoImagemId, MarcaDaguaDTO.Posicao posicao, int larguraPercentual,
        int opacidadePercentual, int margemPercentual, Instant capturadoEm
) {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

    public static @org.jspecify.annotations.Nullable MarcaDaguaSnapshotDTO fromJson(@org.jspecify.annotations.Nullable String json) {
        if (json == null) return null;
        try { return MAPPER.readValue(json, MarcaDaguaSnapshotDTO.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Snapshot de marca d'água inválido."); }
    }
}
