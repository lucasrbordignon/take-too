package com.kinkan.take_too.domain.dto.auth;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MagicLinkRequestDTO(
        @NotNull UUID projetoId
) {
}
