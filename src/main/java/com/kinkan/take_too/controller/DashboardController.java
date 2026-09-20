package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.DashboardMetricasDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Endpoints para métricas consolidadas e visão geral do profissional")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metricas")
    @Operation(summary = "Obtém métricas consolidadas e indicadores de crescimento para o dashboard")
    public ResponseEntity<DashboardMetricasDTO> obterMetricas(
            @AuthenticationPrincipal @NonNull CustomUserDetails user) {
        return ResponseEntity.ok(dashboardService.obterMetricas(user.getId()));
    }
}
