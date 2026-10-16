package com.kinkan.take_too.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinkan.take_too.config.ImagemProperties;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.*;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.storage.ImageStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.imagem.enabled=false", "app.imagem.provider=fake"})
@AutoConfigureMockMvc
@Import(ImagemIntegrationTest.Config.class)
@Transactional
class ImagemIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ImagemProperties properties;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ClienteRepository clientes;
    @Autowired ProjetoRepository projetos;
    @Autowired TokenService tokens;
    @Autowired FakeStorage storage;
    private Profissional profissional;
    private Projeto projeto;
    private String auth;
    private String client;
    private String other;
    private byte[] png;

    @BeforeEach void preparar() throws Exception {
        properties.setEnabled(true); properties.setProvider("fake"); properties.setMaxBytes(10485760); properties.setMaxPixels(25000000);
        storage.objects.clear(); storage.deleted.clear();
        profissional = professional(); auth = "Bearer " + tokens.generateToken(profissional);
        other = "Bearer " + tokens.generateToken(professional());
        var cliente = new Cliente(); cliente.setNome("Cliente imagem"); cliente.setTelefone(UUID.randomUUID().toString()); cliente = clientes.save(cliente);
        projeto = new Projeto(); projeto.setNome("Projeto imagem"); projeto.setProfissional(profissional); projeto.setCliente(cliente);
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO); projeto.setMagicLinkAtivo(true); projeto = projetos.save(projeto);
        client = "Bearer " + tokens.generateClientToken(cliente, projeto.getId());
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB), "png", bytes); png = bytes.toByteArray();
    }
    private Profissional professional() {
        var p = new Profissional(); p.setNome("Profissional imagem"); p.setEmail(UUID.randomUUID()+"@imagem.test"); p.setSenhaHash("test-hash"); p.setPlano("PRO"); return profissionais.save(p);
    }
    private String body(byte[] bytes) throws Exception {
        return mapper.writeValueAsString(Map.of("nome", "Foto", "tipo", "image/png", "tamanhoBytes", bytes.length,
                "checksum", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))));
    }
    private UUID criar(byte[] bytes, boolean draft) throws Exception {
        String path = draft ? "/api/projetos/"+projeto.getId()+"/imagens/uploads" : "/api/imagens/uploads";
        var response = mvc.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(bytes)))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.imagem.chave").doesNotExist()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).path("imagem").path("id").asText());
    }
    private UUID pronta(boolean draft) throws Exception {
        var id = criar(png, draft); storage.objects.put(storage.lastKey, png);
        mvc.perform(post("/api/imagens/"+id+"/confirmar").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRONTA"))
                .andExpect(jsonPath("$.largura").value(64)).andExpect(jsonPath("$.altura").value(32));
        return id;
    }
    private UUID publicar(UUID image) throws Exception {
        var response = mvc.perform(post("/api/projetos/"+projeto.getId()+"/versoes").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imagemId\":\""+image+"\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoMidia").value("IMAGEM"))
                .andExpect(jsonPath("$.imagemId").value(image.toString())).andExpect(jsonPath("$.arquivoUrl").isEmpty())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).path("id").asText());
    }
    @Test void deveEnviarValidarEPublicarImagemSemPublicacaoAutomatica() throws Exception {
        var image = pronta(true);
        assertEquals(EtapaProjeto.EM_EDICAO, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
        mvc.perform(get("/api/projetos/"+projeto.getId()+"/imagens").header("Authorization", auth)).andExpect(jsonPath("$.totalElements").value(1));
        var version = publicar(image);
        assertEquals(EtapaProjeto.REVISAO_CLIENTE, projetos.findById(projeto.getId()).orElseThrow().getEtapaAtual());
        mvc.perform(get("/api/projetos/"+projeto.getId()+"/imagens").header("Authorization", auth)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/portal/versoes/"+version+"/imagem").header("Authorization", client))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.url").value("https://fake.test/private-image"));
        mvc.perform(delete("/api/imagens/"+image).header("Authorization", auth)).andExpect(status().isBadRequest());
        assertTrue(storage.deleted.isEmpty());
    }
    @Test void deveIsolarBibliotecaUploadEVisualizacao() throws Exception {
        var image = pronta(false);
        mvc.perform(get("/api/imagens")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/imagens").header("Authorization", client)).andExpect(status().isForbidden());
        mvc.perform(post("/api/imagens/"+image+"/visualizacao").header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/imagens/"+image).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/imagens/"+image+"/visualizacao").header("Authorization", auth)).andExpect(status().isOk());
    }
    @Test void naoDevePublicarUploadPendenteNemMultiplasOrigens() throws Exception {
        var image = criar(png, false);
        mvc.perform(post("/api/projetos/"+projeto.getId()+"/versoes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"imagemId\":\""+image+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/projetos/"+projeto.getId()+"/versoes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"imagemId\":\""+image+"\",\"videoId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isBadRequest());
        assertEquals(EtapaProjeto.EM_EDICAO, projeto.getEtapaAtual());
    }
    @Test void deveRejeitarArquivoDisfarcadoEImagemMuitoGrande() throws Exception {
        byte[] invalid = "<html>not an image</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var id = criar(invalid, false); storage.objects.put(storage.lastKey, invalid);
        mvc.perform(post("/api/imagens/"+id+"/confirmar").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ERRO"));
        mvc.perform(post("/api/imagens/uploads").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body(png).replace("\"image/png\"", "\"image/svg+xml\""))).andExpect(status().isBadRequest());
        properties.setMaxBytes(10);
        mvc.perform(post("/api/imagens/uploads").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(png))).andExpect(status().isBadRequest());
    }
    @Test void deveValidarChecksumEResolucaoAntesDeLiberarImagem() throws Exception {
        var id = criar(png, false); byte[] altered = png.clone(); altered[altered.length-1] ^= 1; storage.objects.put(storage.lastKey, altered);
        mvc.perform(post("/api/imagens/"+id+"/confirmar").header("Authorization", auth)).andExpect(jsonPath("$.status").value("ERRO"));
        id = criar(png, false); storage.objects.put(storage.lastKey, png); properties.setMaxPixels(10);
        mvc.perform(post("/api/imagens/"+id+"/confirmar").header("Authorization", auth)).andExpect(jsonPath("$.status").value("ERRO"));
    }
    @Test void deveComentarImagemSemTimestampEAprovar() throws Exception {
        var version = publicar(pronta(false));
        mvc.perform(post("/api/portal/versoes/"+version+"/comentarios").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Ajustar contraste\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.timestampSegundos").isEmpty());
        mvc.perform(post("/api/portal/versoes/"+version+"/comentarios").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Ajustar\",\"tempoVideo\":12}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/portal/versoes/"+version+"/status").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APROVADA\"}")).andExpect(status().isNoContent());
        assertEquals(EtapaProjeto.APROVADO, projeto.getEtapaAtual());
    }
    @Test void deveExcluirImagemNaoPublicadaSemApagarHistorico() throws Exception {
        var id = pronta(false);
        mvc.perform(delete("/api/imagens/"+id).header("Authorization", auth)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/imagens/"+id).header("Authorization", auth)).andExpect(status().isNoContent());
        assertEquals(1, storage.deleted.size());
        mvc.perform(get("/api/imagens").header("Authorization", auth)).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void deveBloquearImagemDeOutroProjetoERevogarMagicLink() throws Exception {
        var version = publicar(pronta(false));
        var outroProjeto = new Projeto(); outroProjeto.setNome("Outro projeto"); outroProjeto.setProfissional(profissional);
        outroProjeto.setCliente(projeto.getCliente()); outroProjeto.setEtapaAtual(EtapaProjeto.EM_EDICAO); outroProjeto.setMagicLinkAtivo(true);
        outroProjeto = projetos.save(outroProjeto);
        String outroToken = "Bearer " + tokens.generateClientToken(projeto.getCliente(), outroProjeto.getId());
        mvc.perform(post("/api/portal/versoes/"+version+"/imagem").header("Authorization", outroToken)).andExpect(status().isForbidden());
        projeto.setMagicLinkAtivo(false); projetos.save(projeto);
        mvc.perform(post("/api/portal/versoes/"+version+"/imagem").header("Authorization", client)).andExpect(status().isUnauthorized());
    }
    @Test void deveReutilizarImagemSemDuplicarObjetoEOrdenarComentariosPorData() throws Exception {
        var image = pronta(false); var version = publicar(image);
        mvc.perform(post("/api/portal/versoes/"+version+"/comentarios").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Primeiro\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/portal/versoes/"+version+"/comentarios").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Segundo\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/portal/versoes/"+version+"/comentarios").header("Authorization", client))
                .andExpect(jsonPath("$[0].texto").value("Primeiro")).andExpect(jsonPath("$[1].texto").value("Segundo"));
        mvc.perform(patch("/api/portal/versoes/"+version+"/status").header("Authorization", client).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJEITADA\"}")).andExpect(status().isNoContent());
        publicar(image);
        assertEquals(1, storage.objects.size());
    }
    @Test void deveRecuperarConfirmacaoDuplicadaEValidarRascunho() throws Exception {
        var id = pronta(true);
        mvc.perform(post("/api/imagens/"+id+"/confirmar").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRONTA"));
        mvc.perform(post("/api/projetos/"+projeto.getId()+"/imagens/uploads").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(body(png))).andExpect(status().isNotFound());
        projeto.setEtapaAtual(EtapaProjeto.ENTREGUE); projetos.save(projeto);
        mvc.perform(post("/api/projetos/"+projeto.getId()+"/imagens/uploads").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(png))).andExpect(status().isBadRequest());
    }
    @TestConfiguration static class Config { @Bean FakeStorage imageStorage() { return new FakeStorage(); } }
    static class FakeStorage implements ImageStorageProvider {
        final Map<String, byte[]> objects = new HashMap<>(); final Set<String> deleted = new HashSet<>(); String lastKey;
        public String id() { return "fake"; }
        public Upload criarUpload(String key, String type, String checksum, Instant expiry) { lastKey=key; return new Upload("https://fake.test/upload", Map.of("If-None-Match", "*")); }
        public Optional<Objeto> consultar(String key) { return Optional.ofNullable(objects.get(key)).map(bytes -> new Objeto(bytes.length, "image/png")); }
        public InputStream abrir(String key) { return new ByteArrayInputStream(objects.get(key)); }
        public String visualizar(String key, Instant expiry) { return "https://fake.test/private-image"; }
        public void excluir(String key) { deleted.add(key); objects.remove(key); }
    }
}
