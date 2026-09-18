package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.VersaoCreateDTO;
import com.kinkan.take_too.domain.dto.VersaoDTO;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.entity.Versao;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusVersao;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ProjetoRepository;
import com.kinkan.take_too.repository.VersaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersaoServiceTest {

    @Mock
    private VersaoRepository versaoRepository;

    @Mock
    private ProjetoRepository projetoRepository;

    @InjectMocks
    private VersaoService versaoService;

    private UUID profissionalId;
    private UUID projetoId;
    private Projeto projeto;

    @BeforeEach
    void setUp() {
        profissionalId = UUID.randomUUID();
        projetoId = UUID.randomUUID();

        Profissional profissional = new Profissional();
        profissional.setId(profissionalId);

        projeto = new Projeto();
        projeto.setId(projetoId);
        projeto.setProfissional(profissional);
        projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO);
    }

    @Test
    void deveCriarVersaoComSucessoEAvancarProjetoParaRevisaoCliente() {
        VersaoCreateDTO dto = new VersaoCreateDTO("https://storage.com/video_v1.mp4");

        when(projetoRepository.findByIdAndProfissional_IdForUpdate(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));
        when(versaoRepository.findMaxNumeroByProjetoId(Objects.requireNonNull(projetoId))).thenReturn(Optional.empty());
        when(versaoRepository.save(any(Versao.class))).thenAnswer(i -> {
            Versao v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        VersaoDTO resultado = versaoService.criarVersao(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId), dto);

        assertNotNull(resultado);
        assertEquals(1, resultado.numero());
        assertEquals("https://storage.com/video_v1.mp4", resultado.arquivoUrl());
        assertEquals(StatusVersao.DISPONIVEL.name(), resultado.status());
        assertEquals(EtapaProjeto.REVISAO_CLIENTE, projeto.getEtapaAtual());
        verify(projetoRepository, times(1)).save(projeto);
    }

    @Test
    void deveIncrementarNumeroDaVersaoAoCriarNovaVersao() {
        VersaoCreateDTO dto = new VersaoCreateDTO("https://storage.com/video_v2.mp4");

        when(projetoRepository.findByIdAndProfissional_IdForUpdate(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));
        when(versaoRepository.findMaxNumeroByProjetoId(Objects.requireNonNull(projetoId))).thenReturn(Optional.of(1));
        when(versaoRepository.save(any(Versao.class))).thenAnswer(i -> {
            Versao v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        VersaoDTO resultado = versaoService.criarVersao(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId), dto);

        assertEquals(2, resultado.numero());
    }

    @Test
    void deveLancarExcecaoAoCriarVersaoParaProjetoEntregue() {
        projeto.setEtapaAtual(EtapaProjeto.ENTREGUE);
        VersaoCreateDTO dto = new VersaoCreateDTO("https://storage.com/video.mp4");

        when(projetoRepository.findByIdAndProfissional_IdForUpdate(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        assertThrows(IllegalArgumentException.class, () ->
                versaoService.criarVersao(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId), dto));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoCriarVersaoParaProjetoAprovado() {
        projeto.setEtapaAtual(EtapaProjeto.APROVADO);
        VersaoCreateDTO dto = new VersaoCreateDTO("https://storage.com/video.mp4");

        when(projetoRepository.findByIdAndProfissional_IdForUpdate(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        assertThrows(IllegalArgumentException.class, () ->
                versaoService.criarVersao(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId), dto));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoCriarVersaoEmGravacaoConcluida() {
        projeto.setEtapaAtual(EtapaProjeto.GRAVACAO_CONCLUIDA);
        VersaoCreateDTO dto = new VersaoCreateDTO("https://storage.com/video.mp4");

        when(projetoRepository.findByIdAndProfissional_IdForUpdate(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        assertThrows(IllegalArgumentException.class, () ->
                versaoService.criarVersao(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId), dto));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoSeProjetoJaEstaEmRevisaoCliente() {
        projeto.setEtapaAtual(EtapaProjeto.REVISAO_CLIENTE);
        VersaoCreateDTO dto = new VersaoCreateDTO("https://storage.com/video.mp4");

        when(projetoRepository.findByIdAndProfissional_IdForUpdate(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        assertThrows(IllegalArgumentException.class, () ->
                versaoService.criarVersao(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId), dto));

        verify(versaoRepository, never()).save(any());
    }

    @Test
    void deveListarVersoesDoProjeto() {
        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));

        Versao v1 = new Versao();
        v1.setId(UUID.randomUUID());
        v1.setNumero(1);
        v1.setArquivoUrl("url1");
        v1.setStatus(StatusVersao.REJEITADA);

        when(versaoRepository.findByProjeto_IdOrderByNumeroAsc(Objects.requireNonNull(projetoId)))
                .thenReturn(List.of(v1));

        List<VersaoDTO> versoes = versaoService.listarVersoesDoProjeto(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId));

        assertEquals(1, versoes.size());
        assertEquals("url1", versoes.get(0).arquivoUrl());
    }
}
