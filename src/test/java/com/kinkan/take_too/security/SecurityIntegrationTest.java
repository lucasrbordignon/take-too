package com.kinkan.take_too.security;

import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProfissionalRepository profissionalRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProjetoRepository projetoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.kinkan.take_too.repository.RefreshTokenRepository refreshTokenRepository;

    private Profissional profissional;
    private Cliente cliente;
    private Projeto projeto;
    private String tokenProfissional;
    private String tokenCliente;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        projetoRepository.deleteAll();
        clienteRepository.deleteAll();
        profissionalRepository.deleteAll();

        profissional = new Profissional();
        profissional.setNome("Videomaker Teste");
        profissional.setEmail("pro-" + UUID.randomUUID() + "@teste.com");
        profissional.setSenhaHash(passwordEncoder.encode("senha123"));
        profissional.setPlano("PRO");
        profissional = profissionalRepository.save(profissional);

        cliente = new Cliente();
        cliente.setNome("Cliente Teste");
        cliente.setTelefone("55119" + System.currentTimeMillis() % 100000000);
        cliente = clienteRepository.save(cliente);

        projeto = new Projeto();
        projeto.setNome("Projeto Teste");
        projeto.setProfissional(profissional);
        projeto.setCliente(cliente);
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO);
        projeto.setMagicLinkAtivo(true);
        projeto = projetoRepository.save(projeto);

        tokenProfissional = tokenService.generateToken(profissional);
        tokenCliente = tokenService.generateClientToken(cliente, projeto.getId());
    }

    @Test
    void rotaPingDeveSerPublica() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void profissionalAutenticadoNaoDeveTerAcessoAoPortalDoCliente() throws Exception {
        mockMvc.perform(get("/api/portal/projetos/" + projeto.getId())
                .header("Authorization", "Bearer " + tokenProfissional))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteAutenticadoNaoDeveTerAcessoAsRotasInternasDeProfissional() throws Exception {
        mockMvc.perform(get("/api/projetos")
                .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/clientes")
                .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/magic-link")
                .header("Authorization", "Bearer " + tokenCliente)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projetoId\":\"" + projeto.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteAutenticadoDeveAcessarPortalDoClienteComSucesso() throws Exception {
        mockMvc.perform(get("/api/portal/projetos/" + projeto.getId())
                .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projeto.getId().toString()))
                .andExpect(jsonPath("$.nome").value(projeto.getNome()));
    }

    @Test
    void getMeDoProfissionalNaoDeveRetornarSenhaHash() throws Exception {
        mockMvc.perform(get("/api/profissionais/me")
                .header("Authorization", "Bearer " + tokenProfissional))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profissional.getId().toString()))
                .andExpect(jsonPath("$.email").value(profissional.getEmail()))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void clienteComTokenDeOutroClienteNoProjetoDeveSerBloqueadoNoFiltro() throws Exception {
        Cliente outroCliente = new Cliente();
        outroCliente.setNome("Outro Cliente");
        outroCliente.setTelefone("5511999990000");
        outroCliente = clienteRepository.save(outroCliente);

        // Token gerado apontando para outroCliente, mas com o projeto.getId() do projeto que pertence ao cliente original
        String tokenMismatched = tokenService.generateClientToken(outroCliente, projeto.getId());

        mockMvc.perform(get("/api/portal/projetos/" + projeto.getId())
                .header("Authorization", "Bearer " + tokenMismatched))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerarNovoMagicLinkDeveReativarProjetoRevogado() throws Exception {
        projeto.setMagicLinkAtivo(false);
        projetoRepository.save(projeto);

        mockMvc.perform(get("/api/portal/projetos/" + projeto.getId())
                .header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());

        var result = mockMvc.perform(post("/api/auth/magic-link")
                .header("Authorization", "Bearer " + tokenProfissional)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projetoId\":\"" + projeto.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        var projetoAtualizado = projetoRepository.findById(projeto.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(projetoAtualizado.isMagicLinkAtivo());

        String responseString = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseString);
        String novoTokenCliente = rootNode.get("token").asText();

        mockMvc.perform(get("/api/portal/projetos/" + projeto.getId())
                .header("Authorization", "Bearer " + novoTokenCliente))
                .andExpect(status().isOk());
    }

    @Test
    void fluxoCompletoDeLoginERefreshToken() throws Exception {
        // 1. Login com credenciais válidas
        var loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + profissional.getEmail() + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        var jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var rootNode = jsonMapper.readTree(loginResponse);
        String primeiroRefreshToken = rootNode.get("refreshToken").asText();
        String primeiroAccessToken = rootNode.get("token").asText();

        // 2. Testar chamada usando primeiro Access Token
        mockMvc.perform(get("/api/profissionais/me")
                .header("Authorization", "Bearer " + primeiroAccessToken))
                .andExpect(status().isOk());

        // 3. Renovar o Access Token via POST /api/auth/refresh usando o Refresh Token
        var refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + primeiroRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String refreshResponse = refreshResult.getResponse().getContentAsString();
        var refreshNode = jsonMapper.readTree(refreshResponse);
        String segundoRefreshToken = refreshNode.get("refreshToken").asText();
        String segundoAccessToken = refreshNode.get("token").asText();

        // Tokens devem ser diferentes (rotação)
        org.junit.jupiter.api.Assertions.assertNotEquals(primeiroRefreshToken, segundoRefreshToken);

        // 4. Testar chamada usando o novo Access Token
        mockMvc.perform(get("/api/profissionais/me")
                .header("Authorization", "Bearer " + segundoAccessToken))
                .andExpect(status().isOk());

        // 5. Tentar reutilizar o primeiro Refresh Token (deve ser rejeitado com 401 por rotação anti-replay)
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + primeiroRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // 6. Fazer logout
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + segundoRefreshToken + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"));

        // 7. Tentar renovar após logout deve retornar 401
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + segundoRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profissionalDeveListarAtividadesDoProjeto() throws Exception {
        mockMvc.perform(get("/api/projetos/" + projeto.getId() + "/atividades")
                .header("Authorization", "Bearer " + tokenProfissional))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].tipo").value("PROJETO_CRIADO"))
                .andExpect(jsonPath("$[0].titulo").value("Projeto criado"));
    }
}
