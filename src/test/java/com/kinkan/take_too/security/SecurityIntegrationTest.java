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

    private Profissional profissional;
    private Cliente cliente;
    private Projeto projeto;
    private String tokenProfissional;
    private String tokenCliente;

    @BeforeEach
    void setUp() {
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
}
