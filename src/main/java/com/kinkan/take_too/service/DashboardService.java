package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.DashboardMetricasDTO;
import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProjetoRepository projetoRepository;
    private final ClienteRepository clienteRepository;
    private final ProjetoService projetoService;

    @Transactional(readOnly = true)
    public DashboardMetricasDTO obterMetricas(@NonNull UUID profissionalId) {
        List<Projeto> projetos = projetoRepository.findByProfissional_Id(profissionalId);
        long totalClientes = clienteRepository.findByProfissionais_Id(profissionalId).size();
        long totalProjetos = projetos.size();

        long projetosEmAndamento = projetos.stream()
                .filter(p -> p.getEtapaAtual() == EtapaProjeto.GRAVACAO_CONCLUIDA || p.getEtapaAtual() == EtapaProjeto.EM_EDICAO)
                .count();

        long aguardandoAprovacao = projetos.stream()
                .filter(p -> p.getEtapaAtual() == EtapaProjeto.REVISAO_CLIENTE)
                .count();

        long concluidos = projetos.stream()
                .filter(p -> p.getEtapaAtual() == EtapaProjeto.APROVADO || p.getEtapaAtual() == EtapaProjeto.ENTREGUE)
                .count();

        Instant agora = Instant.now();
        Instant seteDiasAtras = agora.minus(7, ChronoUnit.DAYS);
        Instant doisDiasAtras = agora.minus(2, ChronoUnit.DAYS);
        Instant trintaDiasAtras = agora.minus(30, ChronoUnit.DAYS);

        long projetosNovosEstaSemana = projetos.stream()
                .filter(p -> p.getCriadoEm() != null && p.getCriadoEm().isAfter(seteDiasAtras))
                .count();

        long aguardandoMaisDeDoisDias = projetos.stream()
                .filter(p -> p.getEtapaAtual() == EtapaProjeto.REVISAO_CLIENTE)
                .filter(p -> {
                    Instant ref = p.getAtualizadoEm() != null ? p.getAtualizadoEm() : p.getCriadoEm();
                    return ref != null && ref.isBefore(doisDiasAtras);
                })
                .count();

        long concluidosEsteMes = projetos.stream()
                .filter(p -> p.getEtapaAtual() == EtapaProjeto.APROVADO || p.getEtapaAtual() == EtapaProjeto.ENTREGUE)
                .filter(p -> {
                    Instant ref = p.getAtualizadoEm() != null ? p.getAtualizadoEm() : p.getCriadoEm();
                    return ref != null && ref.isAfter(trintaDiasAtras);
                })
                .count();

        List<ProjetoDTO> projetosRecentes = projetos.stream()
                .sorted(Comparator.comparing((Projeto p) -> {
                    if (p.getAtualizadoEm() != null) return p.getAtualizadoEm();
                    if (p.getCriadoEm() != null) return p.getCriadoEm();
                    return Instant.EPOCH;
                }).reversed())
                .limit(5)
                .map(projetoService::toDTO)
                .toList();

        return new DashboardMetricasDTO(
                totalProjetos,
                projetosEmAndamento,
                aguardandoAprovacao,
                concluidos,
                totalClientes,
                projetosNovosEstaSemana,
                aguardandoMaisDeDoisDias,
                concluidosEsteMes,
                projetosRecentes
        );
    }
}
