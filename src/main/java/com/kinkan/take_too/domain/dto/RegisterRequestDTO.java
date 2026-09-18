package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String senha,
        @NotBlank String plano
) {
}
