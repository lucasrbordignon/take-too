package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.domain.dto.DashboardMetricasDTO;
import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProjetoRepository projetoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ProjetoService projetoService;

    @InjectMocks
    private DashboardService dashboardService;

    private final UUID profissionalId = UUID.randomUUID();
    private Profissional profissional;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        profissional = new Profissional();
        profissional.setId(profissionalId);
        profissional.setNome("Profissional Teste");

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Cliente Teste");
    }

    @Test
    void deveCalcularMetricasDoDashboardComSucesso() {
        Instant agora = Instant.now();

        // 1. Projeto em edição (criado há 1 dia -> novo esta semana)
        Projeto p1 = new Projeto();
        p1.setId(UUID.randomUUID());
        p1.setNome("Projeto 1");
        p1.setProfissional(profissional);
        p1.setCliente(cliente);
        p1.setEtapaAtual(EtapaProjeto.EM_EDICAO);
        p1.setCriadoEm(agora.minus(1, ChronoUnit.DAYS));
        p1.setAtualizadoEm(agora.minus(1, ChronoUnit.DAYS));

        // 2. Projeto em revisão do cliente (atualizado há 3 dias -> aguardando há mais de 2 dias)
        Projeto p2 = new Projeto();
        p2.setId(UUID.randomUUID());
        p2.setNome("Projeto 2");
        p2.setProfissional(profissional);
        p2.setCliente(cliente);
        p2.setEtapaAtual(EtapaProjeto.REVISAO_CLIENTE);
        p2.setCriadoEm(agora.minus(10, ChronoUnit.DAYS));
        p2.setAtualizadoEm(agora.minus(3, ChronoUnit.DAYS));

        // 3. Projeto aprovado (atualizado há 5 dias -> concluído este mês)
        Projeto p3 = new Projeto();
        p3.setId(UUID.randomUUID());
        p3.setNome("Projeto 3");
        p3.setProfissional(profissional);
        p3.setCliente(cliente);
        p3.setEtapaAtual(EtapaProjeto.APROVADO);
        p3.setCriadoEm(agora.minus(20, ChronoUnit.DAYS));
        p3.setAtualizadoEm(agora.minus(5, ChronoUnit.DAYS));

        when(projetoRepository.findByProfissional_Id(profissionalId)).thenReturn(List.of(p1, p2, p3));
        when(clienteRepository.findByProfissionais_Id(profissionalId)).thenReturn(List.of(cliente));

        ClienteDTO clienteDTO = new ClienteDTO(cliente.getId(), cliente.getNome(), "11999999999", null, agora);
        ProjetoDTO p1DTO = new ProjetoDTO(p1.getId(), p1.getNome(), p1.getEtapaAtual(), clienteDTO, p1.getCriadoEm(), p1.getAtualizadoEm(), true);
        ProjetoDTO p2DTO = new ProjetoDTO(p2.getId(), p2.getNome(), p2.getEtapaAtual(), clienteDTO, p2.getCriadoEm(), p2.getAtualizadoEm(), true);
        ProjetoDTO p3DTO = new ProjetoDTO(p3.getId(), p3.getNome(), p3.getEtapaAtual(), clienteDTO, p3.getCriadoEm(), p3.getAtualizadoEm(), true);

        when(projetoService.toDTO(p1)).thenReturn(p1DTO);
        when(projetoService.toDTO(p2)).thenReturn(p2DTO);
        when(projetoService.toDTO(p3)).thenReturn(p3DTO);

        DashboardMetricasDTO metricas = dashboardService.obterMetricas(profissionalId);

        assertNotNull(metricas);
        assertEquals(3, metricas.totalProjetos());
        assertEquals(1, metricas.projetosEmAndamento());
        assertEquals(1, metricas.aguardandoAprovacao());
        assertEquals(1, metricas.concluidos());
        assertEquals(1, metricas.totalClientes());

        // Indicadores de crescimento
        assertEquals(1, metricas.projetosNovosEstaSemana());
        assertEquals(1, metricas.aguardandoMaisDeDoisDias());
        assertEquals(1, metricas.concluidosEsteMes());

        // Projetos recentes ordenados por data decrescente (p1 -> p2 -> p3)
        assertEquals(3, metricas.projetosRecentes().size());
        assertEquals("Projeto 1", metricas.projetosRecentes().get(0).nome());
    }
}
