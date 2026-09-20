package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ClienteCreateDTO;
import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private com.kinkan.take_too.repository.ProjetoRepository projetoRepository;

    @InjectMocks
    private ClienteService clienteService;

    private UUID profissionalId;
    private Profissional profissional;

    @BeforeEach
    void setUp() {
        profissionalId = UUID.randomUUID();
        profissional = new Profissional();
        profissional.setId(profissionalId);
        profissional.setNome("Videomaker");
        profissional.setClientes(new ArrayList<>());
    }

    @Test
    void deveNormalizarTelefoneAoCriarClienteNovo() {
        ClienteCreateDTO dto = new ClienteCreateDTO("Lucas", "(11) 98765-4321", "lucas@teste.com");

        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId))).thenReturn(Optional.of(profissional));
        // A busca no banco deve ocorrer com o telefone normalizado "5511987654321"
        when(clienteRepository.findByTelefone("5511987654321")).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ClienteDTO resultado = clienteService.criarCliente(Objects.requireNonNull(profissionalId), dto);

        assertNotNull(resultado);
        assertEquals("5511987654321", resultado.telefone());
        verify(clienteRepository, times(1)).save(argThat(c -> "5511987654321".equals(c.getTelefone())));
    }

    @Test
    void deveReaproveitarClienteExistenteComFormatoDiferenteDeTelefone() {
        // Cliente já cadastrado no banco com telefone normalizado
        Cliente clienteExistente = new Cliente();
        clienteExistente.setId(UUID.randomUUID());
        clienteExistente.setNome("Lucas");
        clienteExistente.setTelefone("5511987654321");
        clienteExistente.setProfissionais(new ArrayList<>());

        // Novo profissional tenta cadastrar usando outro formato: "+55 11 98765-4321"
        ClienteCreateDTO dto = new ClienteCreateDTO("Lucas", "+55 11 98765-4321", "lucas@teste.com");

        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId))).thenReturn(Optional.of(profissional));
        when(clienteRepository.findByTelefone("5511987654321")).thenReturn(Optional.of(clienteExistente));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteDTO resultado = clienteService.criarCliente(Objects.requireNonNull(profissionalId), dto);

        assertNotNull(resultado);
        assertEquals(clienteExistente.getId(), resultado.id());
        assertTrue(clienteExistente.getProfissionais().contains(profissional));
        verify(clienteRepository, times(1)).save(clienteExistente);
    }

    @Test
    void deveLancarExcecaoSeClienteJaPertenceAoPortfolioDoProfissional() {
        Cliente clienteExistente = new Cliente();
        clienteExistente.setId(UUID.randomUUID());
        clienteExistente.setNome("Lucas");
        clienteExistente.setTelefone("5511987654321");
        clienteExistente.setProfissionais(new ArrayList<>());
        clienteExistente.getProfissionais().add(profissional);

        ClienteCreateDTO dto = new ClienteCreateDTO("Lucas", "11987654321", "lucas@teste.com");

        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId))).thenReturn(Optional.of(profissional));
        when(clienteRepository.findByTelefone("5511987654321")).thenReturn(Optional.of(clienteExistente));

        assertThrows(IllegalArgumentException.class, () ->
                clienteService.criarCliente(Objects.requireNonNull(profissionalId), dto));

        verify(clienteRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoTelefoneNaoContemDigitosValidos() {
        ClienteCreateDTO dto = new ClienteCreateDTO("Lucas", "abc", "lucas@teste.com");

        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId))).thenReturn(Optional.of(profissional));

        assertThrows(IllegalArgumentException.class, () ->
                clienteService.criarCliente(Objects.requireNonNull(profissionalId), dto));

        verify(clienteRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoQuandoTelefonePossuiTamanhoInvalido() {
        ClienteCreateDTO dto = new ClienteCreateDTO("Lucas", "123456", "lucas@teste.com");

        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId))).thenReturn(Optional.of(profissional));

        assertThrows(IllegalArgumentException.class, () ->
                clienteService.criarCliente(Objects.requireNonNull(profissionalId), dto));

        verify(clienteRepository, never()).save(any());
    }

    @Test
    void deveListarClientesComTotalDeProjetosEDataDeAtividade() {
        Cliente c = new Cliente();
        c.setId(UUID.randomUUID());
        c.setNome("Cliente Alpha");
        c.setTelefone("5511999998888");
        c.setEmail("alpha@teste.com");
        c.setCriadoEm(java.time.Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS));

        com.kinkan.take_too.domain.entity.Projeto p = new com.kinkan.take_too.domain.entity.Projeto();
        p.setId(UUID.randomUUID());
        p.setNome("Projeto Vídeo Alpha");
        p.setCliente(c);
        p.setCriadoEm(java.time.Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));
        p.setAtualizadoEm(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));

        when(clienteRepository.findByProfissionais_Id(profissionalId)).thenReturn(List.of(c));
        when(projetoRepository.findByProfissional_Id(profissionalId)).thenReturn(List.of(p));

        var lista = clienteService.listarClientes(profissionalId);

        assertNotNull(lista);
        assertEquals(1, lista.size());
        ClienteDTO dto = lista.get(0);
        assertEquals("Cliente Alpha", dto.nome());
        assertEquals(1, dto.totalProjetos());
        assertNotNull(dto.ultimaAtividadeEm());
        assertEquals(p.getAtualizadoEm(), dto.ultimaAtividadeEm());
    }

    @Test
    void deveBuscarClientePorIdComSucesso() {
        UUID clienteId = UUID.randomUUID();
        Cliente c = new Cliente();
        c.setId(clienteId);
        c.setNome("Cliente Beta");
        c.setTelefone("5511977776666");
        c.setCriadoEm(java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS));

        when(clienteRepository.findByIdAndProfissionais_Id(clienteId, profissionalId)).thenReturn(Optional.of(c));
        when(projetoRepository.findByProfissional_Id(profissionalId)).thenReturn(List.of());

        var dto = clienteService.buscarPorId(profissionalId, clienteId);

        assertNotNull(dto);
        assertEquals("Cliente Beta", dto.nome());
        assertEquals(0, dto.totalProjetos());
        assertEquals(c.getCriadoEm(), dto.ultimaAtividadeEm());
    }
}
