package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.entity.Versao;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusVersao;
import com.kinkan.take_too.exception.ForbiddenException;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ComentarioRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortalClienteServiceTest {

    @Mock
    private ProjetoRepository projetoRepository;

    @Mock
    private VersaoRepository versaoRepository;

    @Mock
    private ComentarioRepository comentarioRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private PortalClienteService portalClienteService;

    private UUID projetoId;
    private UUID versaoId;
    private Projeto projeto;
    private Versao versao;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        projetoId = UUID.randomUUID();
        versaoId = UUID.randomUUID();

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Cliente Teste");
        cliente.setTelefone("5511999998888");

        projeto = new Projeto();
        projeto.setId(projetoId);
        projeto.setNome("Vídeo Comercial");
        projeto.setCliente(cliente);
        projeto.setEtapaAtual(EtapaProjeto.REVISAO_CLIENTE);

        versao = new Versao();
        versao.setId(versaoId);
        versao.setNumero(1);
        versao.setArquivoUrl("https://storage.com/v1.mp4");
        versao.setStatus(StatusVersao.DISPONIVEL);
        versao.setProjeto(projeto);
    }

    @Test
    void deveAprovarVersaoComSucessoEAvancarProjetoParaAprovado() {
        when(versaoRepository.findById(Objects.requireNonNull(versaoId))).thenReturn(Optional.of(versao));
        when(versaoRepository.findTopByProjeto_IdOrderByNumeroDesc(Objects.requireNonNull(projetoId))).thenReturn(Optional.of(versao));

        portalClienteService.alterarStatusVersao(Objects.requireNonNull(projetoId), Objects.requireNonNull(versaoId), StatusVersao.APROVADA);

        assertEquals(StatusVersao.APROVADA, versao.getStatus());
        assertEquals(EtapaProjeto.APROVADO, projeto.getEtapaAtual());
        verify(versaoRepository, times(1)).save(versao);
        verify(projetoRepository, times(1)).save(projeto);
    }

    @Test
    void deveRejeitarVersaoComSucessoEVersaoFicarRejeitadaEProjetoEmEdicao() {
        when(versaoRepository.findById(Objects.requireNonNull(versaoId))).thenReturn(Optional.of(versao));
        when(versaoRepository.findTopByProjeto_IdOrderByNumeroDesc(Objects.requireNonNull(projetoId))).thenReturn(Optional.of(versao));

        portalClienteService.alterarStatusVersao(Objects.requireNonNull(projetoId), Objects.requireNonNull(versaoId), StatusVersao.REJEITADA);

        assertEquals(StatusVersao.REJEITADA, versao.getStatus());
        assertEquals(EtapaProjeto.EM_EDICAO, projeto.getEtapaAtual());
        verify(versaoRepository, times(1)).save(versao);
        verify(projetoRepository, times(1)).save(projeto);
    }

    @Test
    void deveRejeitarTentativaDeEnviarStatusDisponivel() {
        assertThrows(IllegalArgumentException.class, () ->
                portalClienteService.alterarStatusVersao(Objects.requireNonNull(projetoId), Objects.requireNonNull(versaoId), StatusVersao.DISPONIVEL));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoSeProjetoNaoEstaEmRevisaoCliente() {
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO);
        when(versaoRepository.findById(Objects.requireNonNull(versaoId))).thenReturn(Optional.of(versao));

        assertThrows(IllegalStateException.class, () ->
                portalClienteService.alterarStatusVersao(Objects.requireNonNull(projetoId), Objects.requireNonNull(versaoId), StatusVersao.APROVADA));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoTentarAvaliarVersaoAntiga() {
        Versao novaVersao = new Versao();
        novaVersao.setId(UUID.randomUUID());
        novaVersao.setNumero(2);

        when(versaoRepository.findById(Objects.requireNonNull(versaoId))).thenReturn(Optional.of(versao));
        when(versaoRepository.findTopByProjeto_IdOrderByNumeroDesc(Objects.requireNonNull(projetoId))).thenReturn(Optional.of(novaVersao));

        assertThrows(IllegalArgumentException.class, () ->
                portalClienteService.alterarStatusVersao(Objects.requireNonNull(projetoId), Objects.requireNonNull(versaoId), StatusVersao.APROVADA));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoTentarAvaliarVersaoJaFinalizada() {
        versao.setStatus(StatusVersao.APROVADA);
        when(versaoRepository.findById(Objects.requireNonNull(versaoId))).thenReturn(Optional.of(versao));
        when(versaoRepository.findTopByProjeto_IdOrderByNumeroDesc(Objects.requireNonNull(projetoId))).thenReturn(Optional.of(versao));

        assertThrows(IllegalStateException.class, () ->
                portalClienteService.alterarStatusVersao(Objects.requireNonNull(projetoId), Objects.requireNonNull(versaoId), StatusVersao.REJEITADA));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveBloquearAcessoComTokenDeOutroProjeto() {
        UUID outroProjetoId = UUID.randomUUID();

        assertThrows(ForbiddenException.class, () ->
                portalClienteService.buscarProjeto(Objects.requireNonNull(outroProjetoId), Objects.requireNonNull(projetoId)));
    }
}
