package com.kinkan.take_too.domain.dto;

import com.kinkan.take_too.domain.entity.VideoAsset;
import com.kinkan.take_too.domain.enums.StatusVideo;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record VideoVersaoDTO(UUID id, StatusVideo status, @Nullable Double duracaoSegundos,
                             @Nullable Integer largura, @Nullable Integer altura,
                             @Nullable String proporcao, @Nullable String orientacao) {
    public static VideoVersaoDTO from(VideoAsset video) {
        String orientacao = null;
        if (video.getLargura() != null && video.getAltura() != null) {
            double aspect = (double) video.getLargura() / video.getAltura();
            String ratio = video.getProporcao();
            if (ratio != null && ratio.matches("[1-9][0-9]*:[1-9][0-9]*")) {
                String[] parts = ratio.split(":");
                aspect = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            }
            orientacao = aspect < 1 ? "VERTICAL" : aspect > 1 ? "PAISAGEM" : "QUADRADO";
        }
        return new VideoVersaoDTO(video.getId(), video.getStatus(), video.getDuracaoSegundos(),
                video.getLargura(), video.getAltura(), video.getProporcao(), orientacao);
    }
}
