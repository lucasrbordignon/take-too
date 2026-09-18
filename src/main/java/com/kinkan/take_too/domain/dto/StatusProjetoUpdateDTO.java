package com.kinkan.take_too.domain.dto;

import com.kinkan.take_too.domain.enums.EtapaProjeto;
import jakarta.validation.constraints.NotNull;

public record StatusProjetoUpdateDTO(
        @NotNull EtapaProjeto status
) {
}
