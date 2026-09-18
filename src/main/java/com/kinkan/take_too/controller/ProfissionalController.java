package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/profissionais")
@RequiredArgsConstructor
@Tag(name = "Profissionais", description = "Endpoints para gerenciamento do profissional logado")
public class ProfissionalController {

    private final ProfissionalRepository profissionalRepository;

    @GetMapping("/me")
    @Operation(summary = "Retorna os dados do profissional logado", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<com.kinkan.take_too.domain.dto.ProfissionalDTO> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        var profissional = profissionalRepository.findById(Objects.requireNonNull(userDetails.getId())).orElseThrow();
        return ResponseEntity.ok(new com.kinkan.take_too.domain.dto.ProfissionalDTO(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEmail(),
                profissional.getPlano()
        ));
    }
}
