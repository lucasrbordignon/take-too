package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
import org.hibernate.validator.constraints.URL;

public record VersaoCreateDTO(
        @Nullable @URL String arquivoUrl,
        @Nullable UUID videoId
) {
    public VersaoCreateDTO(String arquivoUrl) { this(arquivoUrl, null); }

    @AssertTrue(message = "Informe exatamente uma origem: arquivoUrl válida ou videoId.")
    @JsonIgnore
    public boolean isOrigemValida() {
        return videoId == null ? arquivoUrl != null && !arquivoUrl.isBlank() : arquivoUrl == null;
    }
}
