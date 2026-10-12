package com.kinkan.take_too.service.video.mux;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinkan.take_too.config.MuxProperties;
import com.kinkan.take_too.domain.dto.MarcaDaguaDTO;
import com.kinkan.take_too.domain.enums.StatusVideo;
import com.kinkan.take_too.exception.UnauthorizedException;
import com.kinkan.take_too.exception.VideoProviderException;
import com.kinkan.take_too.service.video.VideoProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class MuxVideoProvider implements VideoProvider {
    private static final Set<String> EVENTOS = Set.of(
            "video.asset.created", "video.asset.ready", "video.asset.errored", "video.asset.deleted",
            "video.upload.asset_created", "video.upload.errored", "video.upload.cancelled", "video.upload.timed_out");
    private final MuxProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Autowired
    public MuxVideoProvider(MuxProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().baseUrl("https://api.mux.com/video/v1")
                .requestFactory(factory).build();
    }

    // Permite testar o protocolo HTTP sem acessar a conta real do Mux.
    MuxVideoProvider(MuxProperties properties, ObjectMapper mapper, RestClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = client;
    }

    @Override
    public String id() { return "mux"; }

    @Override
    public Upload criarUpload(UUID videoId, String origem, @Nullable MarcaDaguaDTO marcaDagua) {
        validarCredenciais();
        if (properties.getUploadTimeoutSeconds() < 60 || properties.getUploadTimeoutSeconds() > 604800) {
            throw new VideoProviderException("Prazo de upload do Mux inválido.");
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("passthrough", videoId.toString());
        settings.put("playback_policies", List.of("signed"));
        settings.put("video_quality", "basic");
        if (marcaDagua != null) {
            settings.put("inputs", List.of(Map.of(), overlay(marcaDagua)));
        }
        Instant inicio = Instant.now();
        JsonNode data = request("POST", "/uploads", Map.of("cors_origin", origem,
                "timeout", properties.getUploadTimeoutSeconds(), "new_asset_settings", settings));
        return upload(data, inicio.plusSeconds(properties.getUploadTimeoutSeconds()));
    }

    @Override
    public Upload consultarUpload(String uploadId) {
        JsonNode data = request("GET", "/uploads/" + identificador(uploadId), null);
        if (!"waiting".equals(text(data, "status"))) {
            throw new IllegalStateException("Este upload não está mais aguardando um arquivo.");
        }
        // A validade original é persistida pela aplicação, não renovada por esta consulta.
        return upload(data, Instant.EPOCH);
    }

    @Override
    public Snapshot consultar(UUID videoId, String uploadId, @Nullable String assetId) {
        if (assetId == null) {
            JsonNode upload = request("GET", "/uploads/" + identificador(uploadId), null);
            String correlation = text(upload.path("new_asset_settings"), "passthrough");
            if (!videoId.toString().equals(correlation)) {
                throw new VideoProviderException("Upload retornado não corresponde ao vídeo.");
            }
            assetId = text(upload, "asset_id");
            if (assetId == null) {
                return switch (Objects.toString(text(upload, "status"), "")) {
                    case "waiting" -> empty(StatusVideo.AGUARDANDO_UPLOAD, null, null);
                    case "cancelled" -> empty(StatusVideo.CANCELADO, null, null);
                    case "timed_out" -> empty(StatusVideo.ERRO, null, "Prazo do upload expirado.");
                    case "errored" -> empty(StatusVideo.ERRO, null, "Não foi possível receber o vídeo.");
                    case "asset_created" -> empty(StatusVideo.PROCESSANDO, null, null);
                    default -> throw new VideoProviderException("Estado de upload não reconhecido.");
                };
            }
        }
        JsonNode asset;
        try {
            asset = request("GET", "/assets/" + identificador(assetId), null);
        } catch (RemoteNotFoundException ex) {
            return empty(StatusVideo.EXCLUIDO, assetId, null);
        }
        if (!videoId.toString().equals(text(asset, "passthrough")) || !assetId.equals(text(asset, "id"))) {
            throw new VideoProviderException("Asset retornado não corresponde ao vídeo.");
        }
        String status = text(asset, "status");
        if ("errored".equals(status)) {
            return empty(StatusVideo.ERRO, assetId, "Não foi possível processar o vídeo.");
        }
        if (!"ready".equals(status)) {
            if (!"preparing".equals(status)) {
                throw new VideoProviderException("Estado de processamento não reconhecido.");
            }
            return empty(StatusVideo.PROCESSANDO, assetId, null);
        }
        String playbackId = null;
        for (JsonNode playback : asset.path("playback_ids")) {
            if ("signed".equals(text(playback, "policy"))) {
                playbackId = text(playback, "id");
                break;
            }
        }
        Integer width = null;
        Integer height = null;
        for (JsonNode track : asset.path("tracks")) {
            if ("video".equals(text(track, "type"))) {
                width = positiveInt(track, "max_width");
                height = positiveInt(track, "max_height");
                break;
            }
        }
        if (playbackId == null || width == null || height == null) {
            return empty(StatusVideo.ERRO, assetId, "Vídeo sem imagem ou sem reprodução privada disponível.");
        }
        Double duration = asset.path("duration").isNumber() ? asset.path("duration").doubleValue() : null;
        if (duration == null || !Double.isFinite(duration) || duration <= 0) {
            throw new VideoProviderException("Duração do vídeo não disponível.");
        }
        return new Snapshot(StatusVideo.PRONTO, assetId, playbackId, duration, width, height,
                text(asset, "aspect_ratio"), null);
    }

    @Override
    public Playback criarPlayback(String playbackId, Instant expiraEm) {
        if (properties.getSigningKeyId().isBlank() || properties.getSigningPrivateKey().isBlank()) {
            throw new VideoProviderException("Chave de reprodução do Mux não configurada.");
        }
        try {
            RSAPrivateKey key = MuxSigningKey.parse(properties.getSigningPrivateKey());
            String token = JWT.create().withKeyId(properties.getSigningKeyId())
                    .withClaim("kid", properties.getSigningKeyId()).withSubject(identificador(playbackId))
                    .withAudience("v").withExpiresAt(expiraEm)
                    .sign(Algorithm.RSA256(null, key));
            String thumbnailToken = JWT.create().withKeyId(properties.getSigningKeyId())
                    .withClaim("kid", properties.getSigningKeyId()).withSubject(identificador(playbackId))
                    .withAudience("t").withExpiresAt(expiraEm).withClaim("width", 640)
                    .sign(Algorithm.RSA256(null, key));
            return new Playback("https://stream.mux.com/" + playbackId + ".m3u8?token=" + token,
                    "application/vnd.apple.mpegurl", expiraEm,
                    "https://image.mux.com/" + playbackId + "/thumbnail.webp?token=" + thumbnailToken);
        } catch (Exception ex) {
            throw new VideoProviderException("Não foi possível assinar a reprodução do vídeo.");
        }
    }

    @Override
    public void excluir(UUID videoId, String uploadId, @Nullable String assetId) {
        if (assetId == null) {
            JsonNode upload;
            try {
                upload = request("GET", "/uploads/" + identificador(uploadId), null);
            } catch (RemoteNotFoundException ex) {
                return;
            }
            if (!videoId.toString().equals(text(upload.path("new_asset_settings"), "passthrough"))) {
                throw new VideoProviderException("Upload retornado não corresponde ao vídeo.");
            }
            assetId = text(upload, "asset_id");
            if (assetId == null && "waiting".equals(text(upload, "status"))) {
                // Se o upload terminar neste intervalo, o Mux rejeita o cancelamento;
                // a chamada pode ser repetida para excluir o asset criado.
                request("PUT", "/uploads/" + identificador(uploadId) + "/cancel", Map.of());
                return;
            }
            if (assetId == null && !Set.of("cancelled", "errored", "timed_out").contains(
                    Objects.toString(text(upload, "status"), ""))) {
                throw new VideoProviderException("Aguarde a identificação do asset antes de excluir o vídeo.");
            }
        }
        if (assetId != null) {
            try {
                request("DELETE", "/assets/" + identificador(assetId), null);
            } catch (RemoteNotFoundException ignored) {
                // Exclusão repetida é idempotente.
            }
        }
    }

    @Override
    public @Nullable Evento verificarWebhook(byte[] corpo, @Nullable String assinatura) {
        verificarAssinatura(corpo, assinatura);
        try {
            JsonNode root = mapper.readTree(corpo);
            if (root == null || !root.isObject() || text(root, "type") == null) {
                throw new IllegalArgumentException("Webhook de vídeo inválido.");
            }
            String type = text(root, "type");
            if (!EVENTOS.contains(type)) { return null; }
            JsonNode data = root.path("data");
            boolean assetEvent = type.startsWith("video.asset.");
            String passthrough = assetEvent ? text(data, "passthrough")
                    : text(data.path("new_asset_settings"), "passthrough");
            UUID videoId = null;
            if (passthrough != null) {
                try { videoId = UUID.fromString(passthrough); }
                catch (IllegalArgumentException ignored) { /* Asset de outra aplicação. */ }
            }
            return new Evento(videoId, assetEvent ? text(data, "upload_id") : text(data, "id"),
                    assetEvent ? text(data, "id") : text(data, "asset_id"));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("Webhook de vídeo inválido.");
        }
    }

    private void verificarAssinatura(byte[] corpo, @Nullable String header) {
        if (properties.getWebhookSecret().isBlank()) {
            throw new VideoProviderException("Webhook de vídeo não configurado.");
        }
        try {
            if (header == null || header.length() > 4096) { throw new IllegalArgumentException(); }
            String timestamp = null;
            List<String> signatures = new ArrayList<>();
            for (String part : header.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2) { continue; }
                if (pair[0].equals("t")) {
                    if (timestamp != null) { throw new IllegalArgumentException(); }
                    timestamp = pair[1];
                }
                if (pair[0].equals("v1")) { signatures.add(pair[1]); }
            }
            long seconds = Long.parseLong(Objects.requireNonNull(timestamp));
            long now = Instant.now().getEpochSecond();
            if (seconds < now - 300 || seconds > now + 300) { throw new IllegalArgumentException(); }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(corpo);
            for (String signature : signatures) {
                if (signature.matches("[0-9a-fA-F]{64}") &&
                        MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) { return; }
            }
        } catch (Exception ignored) {
            // Nunca incluir assinatura, segredo ou corpo no erro.
        }
        throw new UnauthorizedException("Assinatura do webhook de vídeo inválida ou expirada.");
    }

    private Map<String, Object> overlay(MarcaDaguaDTO watermark) {
        String vertical = switch (watermark.posicao()) {
            case SUPERIOR_ESQUERDA, SUPERIOR_DIREITA -> "top";
            case INFERIOR_ESQUERDA, INFERIOR_DIREITA -> "bottom";
            case CENTRO -> "middle";
        };
        String horizontal = switch (watermark.posicao()) {
            case SUPERIOR_ESQUERDA, INFERIOR_ESQUERDA -> "left";
            case SUPERIOR_DIREITA, INFERIOR_DIREITA -> "right";
            case CENTRO -> "center";
        };
        String margin = (watermark.posicao() == MarcaDaguaDTO.Posicao.CENTRO ? 0 : watermark.margemPercentual()) + "%";
        return Map.of("url", watermark.imagemUrl(), "overlay_settings", Map.of(
                "vertical_align", vertical, "horizontal_align", horizontal,
                "vertical_margin", margin, "horizontal_margin", margin,
                "width", watermark.larguraPercentual() + "%", "opacity", watermark.opacidadePercentual() + "%"));
    }

    private Upload upload(JsonNode data, Instant expiry) {
        String id = text(data, "id");
        String url = text(data, "url");
        if (id == null || url == null || !url.startsWith("https://")) {
            throw new VideoProviderException("Resposta de upload do Mux inválida.");
        }
        return new Upload(identificador(id), url, "PUT", "HTTP_CONTENT_RANGE", Map.of(), expiry);
    }

    private Snapshot empty(StatusVideo status, @Nullable String assetId, @Nullable String error) {
        return new Snapshot(status, assetId, null, null, null, null, null, error);
    }

    private void validarCredenciais() {
        if (properties.getTokenId().isBlank() || properties.getTokenSecret().isBlank()) {
            throw new VideoProviderException("Credenciais de vídeo do Mux não configuradas.");
        }
    }

    private JsonNode request(String method, String path, @Nullable Object body) {
        validarCredenciais();
        try {
            var request = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
                    .headers(headers -> headers.setBasicAuth(properties.getTokenId(), properties.getTokenSecret()));
            if (body != null) { request.contentType(MediaType.APPLICATION_JSON).body(body); }
            if (method.equals("DELETE")) {
                request.retrieve().toBodilessEntity();
                return mapper.createObjectNode();
            }
            JsonNode response = request.retrieve().body(JsonNode.class);
            if (response == null || !response.path("data").isObject()) {
                throw new VideoProviderException("Resposta de vídeo do Mux inválida.");
            }
            return response.path("data");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) { throw new RemoteNotFoundException(); }
            throw new VideoProviderException("O provedor de vídeo não conseguiu concluir a operação.");
        } catch (RestClientException ex) {
            throw new VideoProviderException("O provedor de vídeo está temporariamente indisponível.");
        }
    }

    private static String identificador(String id) {
        if (!id.matches("[a-zA-Z0-9_-]{1,255}")) {
            throw new VideoProviderException("Identificador externo de vídeo inválido.");
        }
        return id;
    }

    private static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static @Nullable Integer positiveInt(JsonNode node, String field) {
        return node.path(field).isIntegralNumber() && node.path(field).canConvertToInt()
                && node.path(field).intValue() > 0 ? node.path(field).intValue() : null;
    }

    private static class RemoteNotFoundException extends VideoProviderException {
        RemoteNotFoundException() { super("Vídeo não encontrado no provedor."); }
    }
}
