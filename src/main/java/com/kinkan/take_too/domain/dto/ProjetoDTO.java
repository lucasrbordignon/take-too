package com.kinkan.take_too.domain.dto;

import com.kinkan.take_too.domain.enums.EtapaProjeto;

import java.time.Instant;
import java.util.UUID;

public record ProjetoDTO(
        UUID id,
        String nome,
        EtapaProjeto etapaAtual,
        ClienteDTO cliente,
        Instant criadoEm,
        Instant atualizadoEm,
        boolean magicLinkAtivo
) {
    public ProjetoDTO(UUID id, String nome, EtapaProjeto etapaAtual, ClienteDTO cliente) {
        this(id, nome, etapaAtual, cliente, Instant.now(), Instant.now(), true);
    }
}
