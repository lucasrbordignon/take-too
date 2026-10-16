package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.*;

public record ImagemUploadCreateDTO(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Pattern(regexp = "image/(jpeg|png)") String tipo,
        @Positive long tamanhoBytes,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9+/]{43}=") String checksum) {}
