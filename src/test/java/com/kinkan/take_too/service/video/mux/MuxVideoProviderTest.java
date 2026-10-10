package com.kinkan.take_too.service.video.mux;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinkan.take_too.config.MuxProperties;
import com.kinkan.take_too.domain.dto.MarcaDaguaDTO;
import com.kinkan.take_too.domain.enums.StatusVideo;
import com.kinkan.take_too.exception.UnauthorizedException;
import com.kinkan.take_too.exception.VideoProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MuxVideoProviderTest {
    private MuxProperties properties;
    private MockRestServiceServer server;
    private MuxVideoProvider provider;
    private final UUID videoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new MuxProperties();
        properties.setTokenId("test-id");
        properties.setTokenSecret("test-secret");
        properties.setWebhookSecret("webhook-secret-for-tests");
        var builder = RestClient.builder().baseUrl("https://api.mux.com/video/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new MuxVideoProvider(properties, new ObjectMapper(), builder.build());
    }

    @Test
    void deveCriarUploadPrivadoComCorrelacaoEMarcaDagua() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.cors_origin").value("https://app.example.com"))
                .andExpect(jsonPath("$.new_asset_settings.passthrough").value(videoId.toString()))
                .andExpect(jsonPath("$.new_asset_settings.playback_policies[0]").value("signed"))
                .andExpect(jsonPath("$.new_asset_settings.inputs[0].url").doesNotExist())
                .andExpect(jsonPath("$.new_asset_settings.inputs[1].url").value("https://cdn.example.com/logo.png"))
                .andExpect(jsonPath("$.new_asset_settings.inputs[1].overlay_settings.width").value("20%"))
                .andExpect(jsonPath("$.new_asset_settings.inputs[1].overlay_settings.vertical_align").value("bottom"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"upload-1\",\"url\":\"https://upload.example.com/signed\"}}", MediaType.APPLICATION_JSON));
        var upload = provider.criarUpload(videoId, "https://app.example.com", new MarcaDaguaDTO(
                "https://cdn.example.com/logo.png", MarcaDaguaDTO.Posicao.INFERIOR_DIREITA, 20, 70, 2));
        assertEquals("HTTP_CONTENT_RANGE", upload.protocolo());
        assertEquals("PUT", upload.metodo());
        assertTrue(upload.expiraEm().isAfter(Instant.now().plusSeconds(3500)));
        server.verify();
    }

    @Test
    void deveCriarUploadSemExigirMarcaDagua() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads"))
                .andExpect(jsonPath("$.new_asset_settings.inputs").doesNotExist())
                .andExpect(jsonPath("$.new_asset_settings.playback_policies[0]").value("signed"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"upload-1\",\"url\":\"https://upload.example.com/signed\"}}", MediaType.APPLICATION_JSON));
        assertNotNull(provider.criarUpload(videoId, "https://app.example.com", null));
        server.verify();
    }

    @Test
    void deveEncontrarAssetPeloUploadMesmoSemWebhookEExtrairVideoVertical() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads/upload-1"))
                .andRespond(withSuccess("""
                        {"data":{"status":"asset_created","asset_id":"asset-1",
                        "new_asset_settings":{"passthrough":"%s"}}}
                        """.formatted(videoId), MediaType.APPLICATION_JSON));
        assetReady(1080, 1920, "9:16");
        var snapshot = provider.consultar(videoId, "upload-1", null);
        assertEquals(StatusVideo.PRONTO, snapshot.status());
        assertEquals("private-1", snapshot.playbackId());
        assertEquals(1080, snapshot.largura());
        assertEquals(1920, snapshot.altura());
        assertEquals("9:16", snapshot.proporcao());
        server.verify();
    }

    @Test
    void deveExtrairVideoPaisagemSemUsarPlaybackPublico() {
        assetReady(1920, 1080, "16:9");
        var snapshot = provider.consultar(videoId, "upload-1", "asset-1");
        assertEquals("private-1", snapshot.playbackId());
        assertEquals("16:9", snapshot.proporcao());
        server.verify();
    }

    @Test
    void deveRecusarAssetDeOutraCorrelacao() {
        server.expect(requestTo("https://api.mux.com/video/v1/assets/asset-1"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"asset-1\",\"passthrough\":\"outro\",\"status\":\"ready\"}}", MediaType.APPLICATION_JSON));
        assertThrows(VideoProviderException.class, () -> provider.consultar(videoId, "upload-1", "asset-1"));
        server.verify();
    }

    @Test
    void deveRecusarVideoSemPlaybackAssinado() {
        server.expect(requestTo("https://api.mux.com/video/v1/assets/asset-1"))
                .andRespond(withSuccess("""
                        {"data":{"id":"asset-1","passthrough":"%s","status":"ready","duration":12,
                        "playback_ids":[{"id":"public-1","policy":"public"}],
                        "tracks":[{"type":"video","max_width":1920,"max_height":1080}]}}
                        """.formatted(videoId), MediaType.APPLICATION_JSON));
        assertEquals(StatusVideo.ERRO, provider.consultar(videoId, "upload-1", "asset-1").status());
        server.verify();
    }

    @Test
    void deveTraduzirUploadExpiradoEAssetRemovido() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads/upload-1"))
                .andRespond(withSuccess("""
                        {"data":{"status":"timed_out","new_asset_settings":{"passthrough":"%s"}}}
                        """.formatted(videoId), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mux.com/video/v1/assets/asset-1")).andRespond(withResourceNotFound());
        assertEquals(StatusVideo.ERRO, provider.consultar(videoId, "upload-1", null).status());
        assertEquals(StatusVideo.EXCLUIDO, provider.consultar(videoId, "upload-1", "asset-1").status());
        server.verify();
    }

    @Test
    void deveOcultarRespostaDoProvedorEmErros() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads"))
                .andRespond(withServerError().body("sensitive-token-and-url"));
        var error = assertThrows(VideoProviderException.class, () -> provider.criarUpload(videoId, "https://app.example.com", null));
        assertFalse(error.getMessage().contains("sensitive"));
        assertNull(error.getCause());
        server.verify();
    }

    @Test
    void deveCancelarUploadEExcluirAssetDeFormaIdempotente() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads/upload-1"))
                .andRespond(withSuccess("""
                        {"data":{"status":"waiting","new_asset_settings":{"passthrough":"%s"}}}
                        """.formatted(videoId), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mux.com/video/v1/uploads/upload-1/cancel"))
                .andExpect(method(HttpMethod.PUT)).andRespond(withSuccess("{\"data\":{\"status\":\"cancelled\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mux.com/video/v1/assets/asset-1"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withResourceNotFound());
        provider.excluir(videoId, "upload-1", null);
        provider.excluir(videoId, "upload-1", "asset-1");
        server.verify();
    }

    @Test
    void naoDeveConsiderarExcluidoUmAssetAindaSemReferenciaExterna() {
        server.expect(requestTo("https://api.mux.com/video/v1/uploads/upload-1"))
                .andRespond(withSuccess("""
                        {"data":{"status":"asset_created","new_asset_settings":{"passthrough":"%s"}}}
                        """.formatted(videoId), MediaType.APPLICATION_JSON));
        assertThrows(VideoProviderException.class, () -> provider.excluir(videoId, "upload-1", null));
        server.verify();
    }

    @Test
    void deveVerificarCorpoOriginalEAceitarUmaDasAssinaturas() throws Exception {
        byte[] body = ("{ \"type\":\"video.asset.ready\",\"data\":{\"id\":\"asset-1\",\"passthrough\":\"" + videoId + "\"}}").getBytes(StandardCharsets.UTF_8);
        String signature = assinatura(body, Instant.now().getEpochSecond());
        var event = provider.verificarWebhook(body, signature + ",v1=" + "0".repeat(64));
        assertNotNull(event);
        assertEquals(videoId, event.videoId());
        assertEquals("asset-1", event.assetId());
        byte[] changed = new String(body, StandardCharsets.UTF_8).replace("asset-1", "asset-2").getBytes(StandardCharsets.UTF_8);
        assertThrows(UnauthorizedException.class, () -> provider.verificarWebhook(changed, signature));
    }

    @Test
    void deveRecusarAssinaturaAusenteExpiradaFuturaOuMalformada() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(UnauthorizedException.class, () -> provider.verificarWebhook(body, null));
        for (long offset : new long[]{-301, 301}) {
            String signature = assinatura(body, Instant.now().getEpochSecond() + offset);
            assertThrows(UnauthorizedException.class, () -> provider.verificarWebhook(body, signature));
        }
        assertThrows(UnauthorizedException.class, () -> provider.verificarWebhook(body, "t=abc,v1=abc"));
    }

    @Test
    void deveIgnorarEventoNaoSuportadoSomenteDepoisDeAutenticar() throws Exception {
        byte[] body = "{\"type\":\"video.live_stream.created\"}".getBytes(StandardCharsets.UTF_8);
        assertNull(provider.verificarWebhook(body, assinatura(body, Instant.now().getEpochSecond())));
        assertThrows(UnauthorizedException.class, () -> provider.verificarWebhook(body, null));
    }

    @Test
    void deveAssinarPlaybackComChavesPkcs8EPkcs1EmBase64() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        properties.setSigningKeyId("key-test");
        String pkcs8 = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        // Extrai o OCTET STRING PKCS#1 do envelope PKCS#8 gerado pelo JDK.
        ByteBuffer buffer = ByteBuffer.wrap(pair.getPrivate().getEncoded());
        buffer.get(); readLength(buffer);
        skipTlv(buffer); skipTlv(buffer);
        assertEquals(4, buffer.get());
        byte[] pkcs1Der = new byte[readLength(buffer)];
        buffer.get(pkcs1Der);
        String pkcs1 = pem("RSA PRIVATE KEY", pkcs1Der);
        Instant expires = Instant.now().plusSeconds(600);
        for (String key : new String[]{pkcs8, Base64.getEncoder().encodeToString(pkcs1.getBytes(StandardCharsets.US_ASCII))}) {
            properties.setSigningPrivateKey(key);
            var playback = provider.criarPlayback("private-1", expires);
            var jwt = JWT.require(Algorithm.RSA256((RSAPublicKey) pair.getPublic(), null)).withAudience("v")
                    .withSubject("private-1").build().verify(playback.url().split("token=")[1]);
            assertEquals("key-test", jwt.getKeyId());
            assertEquals(expires.getEpochSecond(), jwt.getExpiresAtAsInstant().getEpochSecond());
            assertEquals("application/vnd.apple.mpegurl", playback.tipo());
        }
    }

    @Test
    void deveFalharSemCredenciaisESemChaveDeReproducao() {
        properties.setTokenSecret("");
        assertThrows(VideoProviderException.class, () -> provider.criarUpload(videoId, "https://app.example.com", null));
        assertThrows(VideoProviderException.class, () -> provider.criarPlayback("private-1", Instant.now().plusSeconds(600)));
    }

    private void assetReady(int width, int height, String ratio) {
        server.expect(requestTo("https://api.mux.com/video/v1/assets/asset-1"))
                .andRespond(withSuccess("""
                        {"data":{"id":"asset-1","status":"ready","passthrough":"%s","duration":42.5,
                        "aspect_ratio":"%s","playback_ids":[{"id":"public-1","policy":"public"},
                        {"id":"private-1","policy":"signed"}],
                        "tracks":[{"type":"audio"},{"type":"video","max_width":%d,"max_height":%d}]}}
                        """.formatted(videoId, ratio, width, height), MediaType.APPLICATION_JSON));
    }

    private String assinatura(byte[] body, long time) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((time + ".").getBytes(StandardCharsets.UTF_8));
        return "t=" + time + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----";
    }

    private static int readLength(ByteBuffer buffer) {
        int first = buffer.get() & 0xff;
        if (first < 128) { return first; }
        int length = 0;
        for (int i = 0; i < (first & 0x7f); i++) { length = length << 8 | buffer.get() & 0xff; }
        return length;
    }

    private static void skipTlv(ByteBuffer buffer) {
        buffer.get();
        int length = readLength(buffer);
        buffer.position(buffer.position() + length);
    }
}
