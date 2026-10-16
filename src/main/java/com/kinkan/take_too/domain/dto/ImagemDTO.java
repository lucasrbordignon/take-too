package com.kinkan.take_too.domain.dto;

import com.kinkan.take_too.domain.entity.ImagemAsset;
import com.kinkan.take_too.domain.enums.StatusImagem;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

public record ImagemDTO(UUID id, String nome, String tipo, long tamanhoBytes,
        @Nullable Integer largura, @Nullable Integer altura, StatusImagem status,
        @Nullable String erro, Instant criadoEm, Instant atualizadoEm, @Nullable MarcaDaguaSnapshotDTO marcaDaguaPlanejada) {
    public static ImagemDTO from(ImagemAsset imagem) {
        return new ImagemDTO(imagem.getId(), imagem.getNome(), imagem.getTipo(), imagem.getTamanhoBytes(),
                imagem.getLargura(), imagem.getAltura(), imagem.getStatus(), imagem.getErro(), imagem.getCriadoEm(), imagem.getAtualizadoEm(), MarcaDaguaSnapshotDTO.fromJson(imagem.getMarcaDaguaPreferenciasJson()));
    }
}
