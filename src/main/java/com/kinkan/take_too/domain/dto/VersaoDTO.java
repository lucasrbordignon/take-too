package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record VersaoDTO(
        UUID id,
        Integer numero,
        String arquivoUrl,
        String status,
        Instant criadoEm
) {
    public VersaoDTO(UUID id, Integer numero, String arquivoUrl, String status) {
        this(id, numero, arquivoUrl, status, Instant.now());
    }
}
