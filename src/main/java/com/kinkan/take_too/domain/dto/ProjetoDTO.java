package com.kinkan.take_too.domain.dto;

import com.kinkan.take_too.domain.enums.EtapaProjeto;

import java.util.UUID;

public record ProjetoDTO(
        UUID id,
        String nome,
        EtapaProjeto etapaAtual,
        ClienteDTO cliente
) {
}
