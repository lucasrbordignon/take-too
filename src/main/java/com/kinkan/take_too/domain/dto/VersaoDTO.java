package com.kinkan.take_too.domain.dto;

import java.util.UUID;

public record VersaoDTO(
                UUID id,
                Integer numero,
                String arquivoUrl,
                String status) {
}
