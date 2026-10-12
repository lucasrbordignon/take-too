package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import com.kinkan.take_too.domain.entity.Versao;

public record VersaoDTO(
        UUID id,
        Integer numero,
        @Nullable String arquivoUrl,
        String status,
        Instant criadoEm,
        String tipoMidia,
        @Nullable UUID videoId,
        @Nullable VideoVersaoDTO video
) {
    public VersaoDTO(UUID id, Integer numero, String arquivoUrl, String status, Instant criadoEm) {
        this(id, numero, arquivoUrl, status, criadoEm, "URL_LEGADA", null, null);
    }

    public VersaoDTO(UUID id, Integer numero, String arquivoUrl, String status) {
        this(id, numero, arquivoUrl, status, Instant.now());
    }

    public static VersaoDTO from(Versao versao) {
        var video = versao.getVideo();
        return new VersaoDTO(versao.getId(), versao.getNumero(), versao.getArquivoUrl(),
                versao.getStatus().name(), versao.getCriadoEm(), video == null ? "URL_LEGADA" : "VIDEO",
                video == null ? null : video.getId(), video == null ? null : VideoVersaoDTO.from(video));
    }
}
