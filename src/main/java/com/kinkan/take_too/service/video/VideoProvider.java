package com.kinkan.take_too.service.video;

import com.kinkan.take_too.domain.dto.MarcaDaguaDTO;
import com.kinkan.take_too.domain.enums.StatusVideo;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Contrato independente do SDK, payload e identificadores públicos do provedor. */
public interface VideoProvider {
    String id();
    Upload criarUpload(UUID videoId, String origem, @Nullable MarcaDaguaDTO marcaDagua);
    Snapshot consultar(UUID videoId, String uploadId, @Nullable String assetId);
    Upload consultarUpload(String uploadId);
    Playback criarPlayback(String playbackId, Instant expiraEm);
    void excluir(UUID videoId, String uploadId, @Nullable String assetId);
    @Nullable Evento verificarWebhook(byte[] corpo, @Nullable String assinatura);

    record Upload(String id, String url, String metodo, String protocolo,
                  Map<String, String> headers, Instant expiraEm) {}
    record Playback(String url, String tipo, Instant expiraEm, @Nullable String miniaturaUrl) {
        public Playback(String url, String tipo, Instant expiraEm) {
            this(url, tipo, expiraEm, null);
        }
    }
    record Snapshot(StatusVideo status, @Nullable String assetId, @Nullable String playbackId,
                    @Nullable Double duracaoSegundos, @Nullable Integer largura, @Nullable Integer altura,
                    @Nullable String proporcao, @Nullable String erro) {}
    record Evento(@Nullable UUID videoId, @Nullable String uploadId, @Nullable String assetId) {}
}
