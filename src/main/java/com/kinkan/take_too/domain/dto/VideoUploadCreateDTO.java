package com.kinkan.take_too.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record VideoUploadCreateDTO(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Size(max = 2048) String origem,
        @Nullable @Valid MarcaDaguaDTO marcaDagua
) {}
