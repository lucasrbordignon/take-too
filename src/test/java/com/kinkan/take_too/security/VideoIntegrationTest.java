package com.kinkan.take_too.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinkan.take_too.config.VideoProperties;
import com.kinkan.take_too.domain.dto.MarcaDaguaDTO;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusVideo;
import com.kinkan.take_too.exception.UnauthorizedException;
import com.kinkan.take_too.exception.VideoProviderException;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.video.VideoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.video.enabled=true", "app.video.provider=fake",
        "app.video.watermark-allowed-hosts=cdn.example.com"})
@AutoConfigureMockMvc
@Import(VideoIntegrationTest.ProvidersConfiguration.class)
class VideoIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ProfissionalRepository profissionais;
    @Autowired private ClienteRepository clientes;
    @Autowired private ProjetoRepository projetos;
    @Autowired private VideoAssetRepository videos;
    @Autowired private VersaoRepository versoes;
    @Autowired private TokenService tokens;
    @Autowired private VideoProperties properties;
    @Autowired @Qualifier("fakeProvider") private FakeProvider fake;
    @Autowired private com.kinkan.take_too.service.VideoService service;

    private Profissional profissional;
    private Projeto projeto;
    private String token;
    private String otherToken;
    private String clientToken;

    @BeforeEach
    void setUp() {
        // IDs únicos evitam interferir nos registros dos testes do fluxo legado.
        profissional = professional();
        token = tokens.generateToken(profissional);
        otherToken = tokens.generateToken(professional());
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente vídeo");
        cliente.setTelefone(UUID.randomUUID().toString());
        cliente = clientes.save(cliente);
        projeto = new Projeto();
        projeto.setNome("Projeto legado");
        projeto.setProfissional(profissional);
        projeto.setCliente(cliente);
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO);
        projeto.setMagicLinkAtivo(true);
        projeto = projetos.save(projeto);
        clientToken = tokens.generateClientToken(cliente, projeto.getId());
        properties.setEnabled(true);
        properties.setProvider("fake");
        fake.failCreate = false;
    }

    @Test
    void deveValidarFluxoSeparadoSemAlterarProjetoOuVersoes() throws Exception {
        UUID id = createVideo();
        mvc.perform(post("/api/videos/" + id + "/playback").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        fake.snapshots.put(id, ready(id, 1080, 1920, "9:16"));
        mvc.perform(post("/api/videos/" + id + "/sincronizar").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRONTO"))
                .andExpect(jsonPath("$.orientacao").value("VERTICAL"))
                .andExpect(jsonPath("$.largura").value(1080));
        mvc.perform(post("/api/videos/" + id + "/playback").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tipo").value("application/vnd.apple.mpegurl"))
                .andExpect(jsonPath("$.url").value("https://fake.example.com/signed.m3u8"))
                .andExpect(jsonPath("$.video.playbackExternoId").doesNotExist());
        assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
        assertTrue(versoes.findByProjeto_IdOrderByNumeroAsc(projeto.getId()).isEmpty());

        // O contrato anterior continua publicando URLs e movendo o projeto para revisão.
        mvc.perform(post("/api/projetos/" + projeto.getId() + "/versoes").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"arquivoUrl\":\"https://www.youtube.com/watch?v=legacy\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.arquivoUrl").value("https://www.youtube.com/watch?v=legacy"));
        assertEquals(EtapaProjeto.REVISAO_CLIENTE, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
    }

    @Test
    void deveIsolarVideosDoProfissionalEmTodasAsOperacoes() throws Exception {
        UUID id = createVideo();
        for (String path : new String[]{"/api/videos/" + id, "/api/videos/" + id + "/upload"}) {
            mvc.perform(get(path).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        }
        for (String suffix : new String[]{"/sincronizar", "/playback"}) {
            mvc.perform(post("/api/videos/" + id + suffix).header("Authorization", bearer(otherToken)))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/videos/" + id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/videos").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deveBloquearClienteENaoAutenticado() throws Exception {
        for (String path : new String[]{"/api/videos", "/api/videos/" + UUID.randomUUID()}) {
            mvc.perform(get(path).header("Authorization", bearer(clientToken))).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/videos/uploads").header("Authorization", bearer(clientToken))
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecuperarSessaoDeUploadSemPersistirOuExporUrlNaConsulta() throws Exception {
        UUID id = createVideo();
        mvc.perform(get("/api/videos/" + id + "/upload").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.protocolo").value("HTTP_CONTENT_RANGE"));
        mvc.perform(get("/api/videos/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").doesNotExist())
                .andExpect(jsonPath("$.provedor").doesNotExist())
                .andExpect(jsonPath("$.uploadExternoId").doesNotExist());
        VideoAsset asset = videos.findById(id).orElseThrow();
        asset.setUploadExpiraEm(Instant.now().minusSeconds(1));
        videos.save(asset);
        mvc.perform(get("/api/videos/" + id + "/upload").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveValidarOrigemMarcaDaguaEPayloadAntesDeChamarProvedor() throws Exception {
        long count = videos.count();
        for (String body : new String[]{
                "{\"nome\":\"\",\"origem\":\"http://localhost:5173\"}",
                "{\"nome\":\"Vídeo\",\"origem\":\"https://evil.example.com\"}",
                """
                {"nome":"Vídeo","origem":"http://localhost:5173","marcaDagua":{
                "imagemUrl":"https://evil.example.com/logo.png","posicao":"CENTRO",
                "larguraPercentual":20,"opacidadePercentual":60,"margemPercentual":2}}
                """,
                """
                {"nome":"Vídeo","origem":"http://localhost:5173","marcaDagua":{
                "imagemUrl":"https://cdn.example.com/logo.png","posicao":"CENTRO",
                "larguraPercentual":101,"opacidadePercentual":60,"margemPercentual":2}}
                """}) {
            mvc.perform(post("/api/videos/uploads").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(count, videos.count());
    }

    @Test
    void devePersistirConfiguracaoDaMarcaDagua() throws Exception {
        mvc.perform(post("/api/videos/uploads").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Com logo","origem":"http://localhost:5173","marcaDagua":{
                "imagemUrl":"https://cdn.example.com/logo.png","posicao":"INFERIOR_DIREITA",
                "larguraPercentual":20,"opacidadePercentual":60,"margemPercentual":2}}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.video.marcaDagua.opacidadePercentual").value(60));
    }

    @Test
    void devePermitirTrocaDeProvedorSemMudarContratoOuVideosExistentes() throws Exception {
        UUID first = createVideo();
        properties.setProvider("alternative");
        UUID second = createVideo();
        assertEquals("fake", videos.findById(first).orElseThrow().getProvedor());
        assertEquals("alternative", videos.findById(second).orElseThrow().getProvedor());
        fake.snapshots.put(first, ready(first, 1920, 1080, "16:9"));
        mvc.perform(post("/api/videos/" + first + "/sincronizar").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orientacao").value("PAISAGEM"));
    }

    @Test
    void deveManterProntoEmConsultasAtrasadasEDuplicadas() throws Exception {
        UUID id = createVideo();
        fake.snapshots.put(id, ready(id, 1080, 1080, "1:1"));
        sync(id);
        Instant before = videos.findById(id).orElseThrow().getAtualizadoEm();
        sync(id);
        assertEquals(before, videos.findById(id).orElseThrow().getAtualizadoEm());
        fake.snapshots.put(id, new VideoProvider.Snapshot(StatusVideo.PROCESSANDO, "asset-" + id, null, null, null, null, null, null));
        sync(id);
        assertEquals(StatusVideo.PRONTO, videos.findById(id).orElseThrow().getStatus());
    }

    @Test
    void deveReconciliarWebhooksDuplicadosSemUsarEstadoOuIdsDoEvento() throws Exception {
        UUID id = createVideo();
        byte[] body = id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        fake.snapshots.put(id, ready(id, 1080, 1920, "9:16"));
        service.receberWebhook("fake", body, "test-signature");
        Instant updated = videos.findById(id).orElseThrow().getAtualizadoEm();
        service.receberWebhook("fake", body, "test-signature");
        assertEquals(updated, videos.findById(id).orElseThrow().getAtualizadoEm());
        assertEquals("asset-" + id, videos.findById(id).orElseThrow().getAssetExternoId());
        assertThrows(UnauthorizedException.class, () -> service.receberWebhook("fake", body, null));
    }

    @Test
    void deveSolicitarRetryParaWebhookDuranteCriacaoDoUpload() {
        VideoAsset video = new VideoAsset();
        video.setId(UUID.randomUUID());
        video.setProfissional(profissional);
        video.setNome("Criando upload");
        video.setProvedor("fake");
        video.setStatus(StatusVideo.CRIANDO_UPLOAD);
        videos.saveAndFlush(video);
        byte[] body = video.getId().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(VideoProviderException.class, () -> service.receberWebhook("fake", body, "test-signature"));
        assertEquals(StatusVideo.CRIANDO_UPLOAD, videos.findById(video.getId()).orElseThrow().getStatus());
    }

    @Test
    void deveExcluirNoProvedorESerIdempotente() throws Exception {
        UUID id = createVideo();
        mvc.perform(delete("/api/videos/" + id).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/videos/" + id).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        assertEquals(1, fake.deletions.get(id));
        assertEquals(StatusVideo.EXCLUIDO, videos.findById(id).orElseThrow().getStatus());
        fake.snapshots.put(id, ready(id, 1920, 1080, "16:9"));
        sync(id);
        assertEquals(StatusVideo.EXCLUIDO, videos.findById(id).orElseThrow().getStatus());
    }

    @Test
    void deveRegistrarFalhaDeCriacaoSemAlterarProjeto() throws Exception {
        fake.failCreate = true;
        mvc.perform(post("/api/videos/uploads").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody())).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/videos").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("ERRO"));
        assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
    }

    @Test
    void deveManterFluxoLegadoDisponivelQuandoVideoEstaDesabilitado() throws Exception {
        properties.setEnabled(false);
        mvc.perform(get("/api/videos").header("Authorization", bearer(token))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/projetos/" + projeto.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void deveRejeitarWebhookSemAssinaturaEComCorpoMuitoGrande() throws Exception {
        mvc.perform(post("/api/webhooks/video/mux").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()); // Mux não configurado no ambiente de testes.
        mvc.perform(post("/api/webhooks/video/mux").contentType(MediaType.APPLICATION_JSON).content("x".repeat(262145)))
                .andExpect(status().isBadRequest());
    }

    private void sync(UUID id) throws Exception {
        mvc.perform(post("/api/videos/" + id + "/sincronizar").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void devePublicarUploadAnteriorEReproduzirNoPortalSemAbrirBiblioteca() throws Exception {
        UUID id = createReadyVideo();
        String version = publish(id);
        mvc.perform(get("/api/portal/projetos/" + projeto.getId() + "/versoes")
                .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].videoId").value(id.toString()))
                .andExpect(jsonPath("$[0].video.orientacao").value("VERTICAL"))
                .andExpect(jsonPath("$[0].tipoMidia").value("VIDEO"))
                .andExpect(jsonPath("$[0].arquivoUrl").isEmpty());
        mvc.perform(post("/api/portal/versoes/" + version + "/playback").header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.url").value("https://fake.example.com/signed.m3u8"));
        mvc.perform(post("/api/projetos/" + projeto.getId() + "/versoes/" + version + "/playback")
                .header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/videos/" + id + "/playback").header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/portal/versoes/" + version + "/comentarios").header("Authorization", bearer(clientToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Ajustar corte\",\"tempoVideo\":12}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/portal/versoes/" + version + "/status").header("Authorization", bearer(clientToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJEITADA\"}"))
                .andExpect(status().isNoContent());
        assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
        // A mesma mídia pode ser usada novamente pelo dono; o histórico permanece.
        publish(id);
        assertEquals(2, versoes.findByProjeto_IdOrderByNumeroAsc(projeto.getId()).size());
    }

    @Test
    void deveBloquearExclusaoDeVideoPublicadoSemChamarProvedor() throws Exception {
        UUID id = createReadyVideo();
        publish(id);
        mvc.perform(delete("/api/videos/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        assertFalse(fake.deletions.containsKey(id));
        assertEquals(StatusVideo.PRONTO, videos.findById(id).orElseThrow().getStatus());
    }

    @Test
    void deveRejeitarPublicacaoSemVideoProntoOuDeOutroProfissional() throws Exception {
        UUID id = createVideo();
        publishRequest(id, token).andExpect(status().isBadRequest());
        fake.snapshots.put(id, ready(id, 1920, 1080, "16:9"));
        sync(id);
        VideoAsset video = videos.findById(id).orElseThrow();
        video.setProfissional(professional());
        videos.save(video);
        publishRequest(id, token).andExpect(status().isNotFound());
        assertTrue(versoes.findByProjeto_IdOrderByNumeroAsc(projeto.getId()).isEmpty());
        assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
    }

    @Test
    void deveRejeitarOrigensAmbiguasOuVazias() throws Exception {
        for (String body : new String[]{"{}", "{\"arquivoUrl\":\"\"}",
                "{\"arquivoUrl\":\"https://example.com/v.mp4\",\"videoId\":\"" + UUID.randomUUID() + "\"}",
                "{\"arquivoUrl\":\"\",\"videoId\":\"" + UUID.randomUUID() + "\"}"}) {
            mvc.perform(post("/api/projetos/" + projeto.getId() + "/versoes").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void deveIsolarPlaybackPorProfissionalEProjetoDoMagicLink() throws Exception {
        String version = publish(createReadyVideo());
        mvc.perform(post("/api/projetos/" + projeto.getId() + "/versoes/" + version + "/playback")
                .header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mvc.perform(post("/api/projetos/" + UUID.randomUUID() + "/versoes/" + version + "/playback")
                .header("Authorization", bearer(token))).andExpect(status().isNotFound());
        Projeto other = new Projeto();
        other.setNome("Outro projeto");
        other.setProfissional(profissional);
        other.setCliente(projeto.getCliente());
        other.setEtapaAtual(EtapaProjeto.EM_EDICAO);
        other.setMagicLinkAtivo(true);
        other = projetos.save(other);
        String otherClient = tokens.generateClientToken(projeto.getCliente(), other.getId());
        mvc.perform(post("/api/portal/versoes/" + version + "/playback").header("Authorization", bearer(otherClient)))
                .andExpect(status().isForbidden());
        projeto.setMagicLinkAtivo(false);
        projetos.save(projeto);
        mvc.perform(post("/api/portal/versoes/" + version + "/playback").header("Authorization", bearer(clientToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveCriarRascunhoRecuperavelNoProjetoSemPublicar() throws Exception {
        var response = mvc.perform(post("/api/projetos/" + projeto.getId() + "/videos/uploads")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(uploadBody()))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        UUID id = UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("video").path("id").asText());
        mvc.perform(get("/api/projetos/" + projeto.getId() + "/videos").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
        assertTrue(versoes.findByProjeto_IdOrderByNumeroAsc(projeto.getId()).isEmpty());
        assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
        fake.snapshots.put(id, ready(id, 1920, 1080, "16:9"));
        sync(id);
        publish(id);
        mvc.perform(get("/api/projetos/" + projeto.getId() + "/videos").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deveDesvincularRascunhoSemExcluirVideoDaBiblioteca() throws Exception {
        var response = mvc.perform(post("/api/projetos/" + projeto.getId() + "/videos/uploads")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(uploadBody()))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(response.getResponse().getContentAsString()).path("video").path("id").asText();
        mvc.perform(delete("/api/projetos/" + projeto.getId() + "/videos/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/videos/" + id).header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/projetos/" + projeto.getId() + "/videos").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(delete("/api/videos/" + id).header("Authorization", bearer(token))).andExpect(status().isNoContent());
    }

    @Test
    void deveIsolarRascunhosERejeitarUploadForaDaEdicao() throws Exception {
        mvc.perform(get("/api/projetos/" + projeto.getId() + "/videos").header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/projetos/" + projeto.getId() + "/videos/uploads").header("Authorization", bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody())).andExpect(status().isNotFound());
        mvc.perform(get("/api/projetos/" + projeto.getId() + "/videos").header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());
        projeto.setEtapaAtual(EtapaProjeto.ENTREGUE);
        projetos.save(projeto);
        mvc.perform(post("/api/projetos/" + projeto.getId() + "/videos/uploads").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody())).andExpect(status().isBadRequest());
    }

    @Test
    void deveImpedirPublicacaoDeVideoExcluido() throws Exception {
        UUID id = createReadyVideo();
        mvc.perform(delete("/api/videos/" + id).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        publishRequest(id, token).andExpect(status().isBadRequest());
        assertTrue(versoes.findByProjeto_IdOrderByNumeroAsc(projeto.getId()).isEmpty());
    }

    private UUID createReadyVideo() throws Exception {
        UUID id = createVideo();
        fake.snapshots.put(id, ready(id, 1080, 1920, "9:16"));
        sync(id);
        return id;
    }

    @Test
    void deveBloquearPlaybackComTokenExpiradoEVideoIndisponivelParaAvaliacao() throws Exception {
        UUID id = createReadyVideo();
        String version = publish(id);
        String expired = com.auth0.jwt.JWT.create().withIssuer("take-too-api")
                .withSubject(projeto.getCliente().getId().toString()).withClaim("role", "ROLE_CLIENTE")
                .withClaim("projetoId", projeto.getId().toString()).withExpiresAt(Instant.now().minusSeconds(60))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("test-secret-key-that-is-at-least-32-chars-long-for-testing-only"));
        mvc.perform(post("/api/portal/versoes/" + version + "/playback").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
        var video = videos.findById(id).orElseThrow();
        video.setStatus(StatusVideo.ERRO);
        videos.save(video);
        mvc.perform(post("/api/portal/versoes/" + version + "/playback").header("Authorization", bearer(clientToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/portal/versoes/" + version + "/status").header("Authorization", bearer(clientToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APROVADA\"}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions publishRequest(UUID id, String jwt) throws Exception {
        return mvc.perform(post("/api/projetos/" + projeto.getId() + "/versoes").header("Authorization", bearer(jwt))
                .contentType(MediaType.APPLICATION_JSON).content("{\"videoId\":\"" + id + "\"}"));
    }

    private String publish(UUID id) throws Exception {
        var response = publishRequest(id, token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.videoId").value(id.toString())).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).path("id").asText();
    }

    private UUID createVideo() throws Exception {
        var result = mvc.perform(post("/api/videos/uploads").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody()))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.video.status").value("AGUARDANDO_UPLOAD"))
                .andReturn();
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.path("video").path("id").asText());
    }

    private String uploadBody() { return "{\"nome\":\"Vídeo teste\",\"origem\":\"http://localhost:5173\"}"; }
    private String bearer(String jwt) { return "Bearer " + jwt; }

    private Profissional professional() {
        Profissional professional = new Profissional();
        professional.setNome("Profissional vídeo");
        professional.setEmail(UUID.randomUUID() + "@example.com");
        professional.setSenhaHash("hash-test");
        professional.setPlano("PRO");
        return profissionais.save(professional);
    }

    private static VideoProvider.Snapshot ready(UUID id, int width, int height, String ratio) {
        return new VideoProvider.Snapshot(StatusVideo.PRONTO, "asset-" + id, "playback-" + id,
                42.5, width, height, ratio, null);
    }

    @TestConfiguration
    static class ProvidersConfiguration {
        @Bean FakeProvider fakeProvider() { return new FakeProvider(); }
        @Bean VideoProvider alternativeProvider() {
            return new FakeProvider() { @Override public String id() { return "alternative"; } };
        }
    }

    static class FakeProvider implements VideoProvider {
        final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
        final Map<UUID, Integer> deletions = new ConcurrentHashMap<>();
        boolean failCreate;
        @Override public String id() { return "fake"; }
        @Override public Upload criarUpload(UUID id, String origin, MarcaDaguaDTO watermark) {
            if (failCreate) { throw new VideoProviderException("Falha simulada."); }
            return new Upload("upload-" + id, "https://fake.example.com/upload", "PUT", "HTTP_CONTENT_RANGE", Map.of(), Instant.now().plusSeconds(3600));
        }
        @Override public Snapshot consultar(UUID id, String upload, String asset) {
            return snapshots.getOrDefault(id, new Snapshot(StatusVideo.AGUARDANDO_UPLOAD, null, null, null, null, null, null, null));
        }
        @Override public Upload consultarUpload(String upload) {
            return new Upload(upload, "https://fake.example.com/upload", "PUT", "HTTP_CONTENT_RANGE", Map.of(), Instant.EPOCH);
        }
        @Override public Playback criarPlayback(String playback, Instant expiry) {
            return new Playback("https://fake.example.com/signed.m3u8", "application/vnd.apple.mpegurl", expiry);
        }
        @Override public void excluir(UUID id, String upload, String asset) { deletions.merge(id, 1, Integer::sum); }
        @Override public Evento verificarWebhook(byte[] body, String signature) {
            if (!"test-signature".equals(signature)) { throw new UnauthorizedException("Assinatura inválida."); }
            return new Evento(UUID.fromString(new String(body, java.nio.charset.StandardCharsets.UTF_8)), null, null);
        }
    }
}
