package com.kinkan.take_too.domain.dto;

import java.util.List;

public record DashboardMetricasDTO(
    long totalProjetos,
    long projetosEmAndamento,
    long aguardandoAprovacao,
    long concluidos,
    long totalClientes,
    long projetosNovosEstaSemana,
    long aguardandoMaisDeDoisDias,
    long concluidosEsteMes,
    List<ProjetoDTO> projetosRecentes
) {}
