package com.kinkan.take_too.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
import org.hibernate.validator.constraints.URL;

public record VersaoCreateDTO(
        @Nullable @URL String arquivoUrl,
        @Nullable UUID videoId,
        @Nullable UUID imagemId
) {
    public VersaoCreateDTO(String arquivoUrl) { this(arquivoUrl, null, null); }
    public VersaoCreateDTO(String arquivoUrl, UUID videoId) { this(arquivoUrl, videoId, null); }

    @AssertTrue(message = "Informe exatamente uma origem: arquivoUrl válida, videoId ou imagemId.")
    @JsonIgnore
    public boolean isOrigemValida() {
        return (arquivoUrl != null ? 1 : 0) + (videoId != null ? 1 : 0) + (imagemId != null ? 1 : 0) == 1
                && (arquivoUrl == null || !arquivoUrl.isBlank());
    }
}
