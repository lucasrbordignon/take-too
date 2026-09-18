package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ProjetoCreateDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.entity.Versao;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.exception.ForbiddenException;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import com.kinkan.take_too.repository.VersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjetoServiceTest {

    @Mock
    private ProjetoRepository projetoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private VersaoRepository versaoRepository;

    @InjectMocks
    private ProjetoService projetoService;

    private final UUID profissionalId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    private Profissional profissional;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        profissional = new Profissional();
        profissional.setId(profissionalId);

        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("Cliente Teste");
    }

    @Test
    @SuppressWarnings("null")
    void deveCriarProjetoComSucessoSeClientePertenceAoProfissional() {
        ProjetoCreateDTO dto = new ProjetoCreateDTO("Novo Projeto", clienteId);

        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(profissional));
        when(clienteRepository.findByIdAndProfissionais_Id(Objects.requireNonNull(clienteId),
                Objects.requireNonNull(profissionalId))).thenReturn(Optional.of(cliente));
        when(projetoRepository.save(any(Projeto.class))).thenAnswer(i -> {
            Projeto p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        var result = projetoService.criarProjeto(Objects.requireNonNull(profissionalId), dto);

        assertEquals("Novo Projeto", result.nome());
        assertEquals(EtapaProjeto.GRAVACAO_CONCLUIDA, result.etapaAtual());
        verify(projetoRepository, times(1)).save(any(Projeto.class));
    }

    @Test
    @SuppressWarnings("null")
    void deveLancarExcecaoAoTentarCriarProjetoParaClienteDeOutroProfissional() {
        ProjetoCreateDTO dto = new ProjetoCreateDTO("Projeto Indevido", clienteId);

        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(profissional));
        when(clienteRepository.findByIdAndProfissionais_Id(Objects.requireNonNull(clienteId),
                Objects.requireNonNull(profissionalId))).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> projetoService.criarProjeto(Objects.requireNonNull(profissionalId), dto));

        verify(projetoRepository, never()).save(any(Projeto.class));
    }

    @Test
    void deveAtualizarStatusComSucessoQuandoTransicaoValida() {
        Projeto projeto = new Projeto();
        projeto.setId(UUID.randomUUID());
        projeto.setEtapaAtual(EtapaProjeto.GRAVACAO_CONCLUIDA);
        projeto.setCliente(cliente);

        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projeto.getId()),
                Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        var result = projetoService.atualizarStatus(Objects.requireNonNull(profissionalId),
                Objects.requireNonNull(projeto.getId()), EtapaProjeto.EM_EDICAO);

        assertEquals(EtapaProjeto.EM_EDICAO, result.etapaAtual());
        verify(projetoRepository, times(1)).save(projeto);
    }

    @Test
    void deveLancarExcecaoAoTentarPularEtapaDeGravacaoParaEntregue() {
        Projeto projeto = new Projeto();
        projeto.setId(UUID.randomUUID());
        projeto.setEtapaAtual(EtapaProjeto.GRAVACAO_CONCLUIDA);
        projeto.setCliente(cliente);

        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projeto.getId()),
                Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        assertThrows(IllegalArgumentException.class,
                () -> projetoService.atualizarStatus(Objects.requireNonNull(profissionalId),
                        Objects.requireNonNull(projeto.getId()), EtapaProjeto.ENTREGUE));

        verify(projetoRepository, never()).save(projeto);
    }

    @Test
    void deveLancarExcecaoAoTentarMudarManualmenteParaRevisaoCliente() {
        Projeto projeto = new Projeto();
        projeto.setId(UUID.randomUUID());
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO);
        projeto.setCliente(cliente);

        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projeto.getId()),
                Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        var ex = assertThrows(IllegalArgumentException.class,
                () -> projetoService.atualizarStatus(Objects.requireNonNull(profissionalId),
                        Objects.requireNonNull(projeto.getId()), EtapaProjeto.REVISAO_CLIENTE));

        assertTrue(ex.getMessage().contains("exclusivamente através da criação de uma nova versão"));
        verify(projetoRepository, never()).save(projeto);
    }

    @Test
    void deveLancarExcecaoAoTentarAlterarProjetoEntregue() {
        Projeto projeto = new Projeto();
        projeto.setId(UUID.randomUUID());
        projeto.setEtapaAtual(EtapaProjeto.ENTREGUE);

        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projeto.getId()),
                Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        assertThrows(IllegalArgumentException.class,
                () -> projetoService.atualizarStatus(Objects.requireNonNull(profissionalId),
                        Objects.requireNonNull(projeto.getId()), EtapaProjeto.REVISAO_CLIENTE));

        verify(projetoRepository, never()).save(projeto);
    }

    @Test
    void deveRevogarMagicLinkComSucesso() {
        Projeto projeto = new Projeto();
        projeto.setId(UUID.randomUUID());
        projeto.setMagicLinkAtivo(true);

        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projeto.getId()),
                Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        projetoService.revogarMagicLink(Objects.requireNonNull(profissionalId),
                Objects.requireNonNull(projeto.getId()));

        assertFalse(projeto.isMagicLinkAtivo());
        verify(projetoRepository, times(1)).save(projeto);
    }
}
