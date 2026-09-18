package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record VersaoCreateDTO(
        @NotBlank @URL String arquivoUrl
) {
}
