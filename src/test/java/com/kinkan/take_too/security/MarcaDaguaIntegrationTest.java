package com.kinkan.take_too.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinkan.take_too.config.ImagemProperties;
import com.kinkan.take_too.config.VideoProperties;
import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.*;
import com.kinkan.take_too.exception.ArmazenamentoException;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.MarcaDaguaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.imagem.enabled=false", "app.imagem.provider=fake", "app.video.enabled=true", "app.video.provider=fake"})
@AutoConfigureMockMvc
@Import({ImagemIntegrationTest.Config.class, VideoIntegrationTest.ProvidersConfiguration.class})
@Transactional
class MarcaDaguaIntegrationTest {
    private static final String PATH = "/api/profissionais/me/marca-dagua";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ImagemProperties imageProperties;
    @Autowired VideoProperties videoProperties;
    @Autowired ProfissionalRepository profissionais;
    @Autowired ClienteRepository clientes;
    @Autowired ProjetoRepository projetos;
    @Autowired ImagemAssetRepository imagens;
    @Autowired VideoAssetRepository videos;
    @Autowired PreferenciasMarcaDaguaRepository preferencias;
    @Autowired TokenService tokens;
    @Autowired MarcaDaguaService service;
    @Autowired ImagemIntegrationTest.FakeStorage storage;
    private Profissional profissional;
    private String auth;
    private String other;
    private String client;
    private byte[] png;

    @BeforeEach void preparar() throws Exception {
        imageProperties.setEnabled(true); imageProperties.setMaxBytes(10485760); imageProperties.setMaxPixels(25000000);
        videoProperties.setWatermarkAllowedHosts(List.of("fake.test"));
        storage.objects.clear(); storage.deleted.clear();
        profissional = profissional(); auth = "Bearer " + tokens.generateToken(profissional);
        other = "Bearer " + tokens.generateToken(profissional());
        var cliente = new Cliente(); cliente.setNome("Cliente"); cliente.setTelefone(UUID.randomUUID().toString()); cliente = clientes.save(cliente);
        var projeto = new Projeto(); projeto.setNome("Marca"); projeto.setProfissional(profissional); projeto.setCliente(cliente);
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO); projeto = projetos.save(projeto);
        client = "Bearer " + tokens.generateClientToken(cliente, projeto.getId());
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB), "png", bytes); png = bytes.toByteArray();
    }
    private Profissional profissional() {
        var p = new Profissional(); p.setNome("Marca"); p.setEmail(UUID.randomUUID()+"@marca.test"); p.setSenhaHash("test"); p.setPlano("PRO"); return profissionais.save(p);
    }
    private String uploadBody() throws Exception {
        return mapper.writeValueAsString(Map.of("nome","Logo", "tipo","image/png", "tamanhoBytes",png.length,
                "checksum",Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(png))));
    }
    private UUID upload(boolean logo) throws Exception {
        var result = mvc.perform(post(logo ? PATH+"/logo/uploads" : "/api/imagens/uploads").header("Authorization",auth)
                .contentType(MediaType.APPLICATION_JSON).content(uploadBody())).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("imagem").path("id").asText());
    }
    private UUID logo() throws Exception {
        var id = upload(true); storage.objects.put(storage.lastKey, png);
        mvc.perform(post("/api/imagens/"+id+"/confirmar").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRONTA"));
        return id;
    }
    private String config(UUID logo, boolean ativa, int largura) throws Exception {
        var body = new LinkedHashMap<String,Object>(); body.put("ativaPadrao",ativa); body.put("logoImagemId",logo);
        body.put("posicao","INFERIOR_DIREITA"); body.put("larguraPercentual",largura); body.put("opacidadePercentual",70); body.put("margemPercentual",3);
        return mapper.writeValueAsString(body);
    }
    private void salvar(UUID logo, boolean ativa, int largura) throws Exception {
        mvc.perform(put(PATH).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(config(logo,ativa,largura)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }

    @Test void deveRetornarDefaultsSemCriarConfiguracao() throws Exception {
        mvc.perform(get(PATH).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.ativaPadrao").value(false))
                .andExpect(jsonPath("$.logoImagemId").isEmpty()).andExpect(jsonPath("$.larguraPercentual").value(15));
        assertFalse(preferencias.existsById(profissional.getId()));
        assertNull(imagens.findById(upload(false)).orElseThrow().getMarcaDaguaPreferenciasJson());
    }
    @Test void deveSalvarTrocarERemoverLogoSemAlterarPerfilOuExporChave() throws Exception {
        var logo = logo(); salvar(logo,true,20);
        mvc.perform(get(PATH).header("Authorization",auth)).andExpect(jsonPath("$.logoImagemId").value(logo.toString()))
                .andExpect(jsonPath("$.logo.chave").doesNotExist()).andExpect(jsonPath("$.logo.url").doesNotExist());
        var nova = logo(); salvar(nova,true,30);
        assertEquals(nova,preferencias.findById(profissional.getId()).orElseThrow().getLogo().getId());
        salvar(null,false,15);
        mvc.perform(delete("/api/imagens/"+logo).header("Authorization",auth)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/imagens/"+nova).header("Authorization",auth)).andExpect(status().isNoContent());
        mvc.perform(get("/api/profissionais/me").header("Authorization",auth)).andExpect(jsonPath("$.nome").value("Marca"));
    }
    @Test void deveIsolarPreferenciasELogoDoProfissional() throws Exception {
        var logo = logo(); salvar(logo,true,20);
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization",client)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).header("Authorization",client).contentType(MediaType.APPLICATION_JSON).content(config(logo,true,20))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization",other)).andExpect(jsonPath("$.ativaPadrao").value(false)).andExpect(jsonPath("$.logoImagemId").isEmpty());
        mvc.perform(put(PATH).header("Authorization",other).contentType(MediaType.APPLICATION_JSON).content(config(logo,true,20))).andExpect(status().isNotFound());
    }
    @Test void deveRejeitarConfiguracaoIncompletaInvalidaOuSemLogoPronta() throws Exception {
        mvc.perform(put(PATH).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(put(PATH).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(config(null,true,15))).andExpect(status().isBadRequest());
        var pending=upload(true);
        mvc.perform(put(PATH).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(config(pending,true,15))).andExpect(status().isBadRequest());
        var logo=logo();
        for (String body : List.of(config(logo,true,0),config(logo,true,101),config(logo,true,15).replace("70","0"),config(logo,true,15).replace("INFERIOR_DIREITA","FORA"))) {
            mvc.perform(put(PATH).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH+"/logo/uploads").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
                .content(uploadBody().replace("\"tamanhoBytes\":"+png.length,"\"tamanhoBytes\":2097153"))).andExpect(status().isBadRequest());
        var asset=imagens.findById(logo).orElseThrow();asset.setLargura(4097);imagens.save(asset);
        mvc.perform(put(PATH).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(config(logo,true,15))).andExpect(status().isBadRequest());
    }
    @Test void deveCapturarPreferenciasEmVideoEImagemSemPersistirUrlNemModificarMidiasAntigas() throws Exception {
        var old=upload(false); var logo=logo();salvar(logo,true,20);
        var imageId=upload(false);var image=imagens.findById(imageId).orElseThrow();String json=image.getMarcaDaguaPreferenciasJson();
        assertNotNull(json);assertFalse(json.contains("http"));assertEquals(logo,image.getLogoMarcaDagua().getId());
        var result=mvc.perform(post("/api/videos/uploads").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Vídeo\",\"origem\":\"http://localhost:5173\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.video.marcaDaguaPlanejada.larguraPercentual").value(20))
                .andExpect(jsonPath("$.video.marcaDagua").isEmpty()).andReturn();
        var videoId=UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("video").path("id").asText());
        String videoJson=videos.findById(videoId).orElseThrow().getMarcaDaguaPreferenciasJson();
        salvar(logo,true,40);
        assertEquals(json,imagens.findById(imageId).orElseThrow().getMarcaDaguaPreferenciasJson());
        assertEquals(videoJson,videos.findById(videoId).orElseThrow().getMarcaDaguaPreferenciasJson());
        assertNull(imagens.findById(old).orElseThrow().getMarcaDaguaPreferenciasJson());
        assertEquals(40,MarcaDaguaSnapshotDTO.fromJson(imagens.findById(upload(false)).orElseThrow().getMarcaDaguaPreferenciasJson()).larguraPercentual());
        salvar(null,false,15);
        mvc.perform(delete("/api/imagens/"+logo).header("Authorization",auth)).andExpect(status().isBadRequest());
        assertTrue(storage.deleted.isEmpty());
    }
    @Test void deveProtegerLogoDasPreferenciasEPermitirExcluirAposDesvincular() throws Exception {
        var logo=logo();salvar(logo,false,15);
        mvc.perform(delete("/api/imagens/"+logo).header("Authorization",auth)).andExpect(status().isBadRequest());
        salvar(null,false,15);
        mvc.perform(delete("/api/imagens/"+logo).header("Authorization",auth)).andExpect(status().isNoContent());
    }
    @Test void uploadDeNovaLogoNaoDeveHerdarMarcaDaLogoAnterior() throws Exception {
        var logo=logo();salvar(logo,true,20);
        var nova=upload(true);
        assertNull(imagens.findById(nova).orElseThrow().getLogoMarcaDagua());
        assertNull(imagens.findById(nova).orElseThrow().getMarcaDaguaPreferenciasJson());
    }
    @Test void devePrepararLogoSomentePorHttpsEmHostPermitidoSemConsultarPreferenciaAtual() throws Exception {
        var logo=logo();salvar(logo,true,20);
        var captura=service.capturar(profissional.getId());assertNotNull(captura);
        var snapshot=MarcaDaguaSnapshotDTO.fromJson(captura.json());assertNotNull(snapshot);
        salvar(null,false,50);
        var input=service.prepararParaProcessamento(profissional.getId(),snapshot);
        assertEquals("https://fake.test/private-image",input.imagemUrl());assertEquals(20,input.larguraPercentual());
        videoProperties.setWatermarkAllowedHosts(List.of("other.test"));
        assertThrows(ArmazenamentoException.class,()->service.prepararParaProcessamento(profissional.getId(),snapshot));
    }
}
