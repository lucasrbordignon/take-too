package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.ProjetoCreateDTO;
import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.dto.StatusProjetoUpdateDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.ProjetoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/projetos")
@RequiredArgsConstructor
@Tag(name = "Projetos", description = "Endpoints para gerenciamento de projetos do profissional logado")
public class ProjetoController {

    private final ProjetoService projetoService;

    @GetMapping
    @Operation(summary = "Lista todos os projetos do profissional logado")
    public ResponseEntity<List<ProjetoDTO>> listar(@AuthenticationPrincipal @NonNull CustomUserDetails user) {
        return ResponseEntity.ok(projetoService.listarProjetos(user.getId()));
    }

    @PostMapping
    @Operation(summary = "Cria um novo projeto para um cliente do profissional")
    public ResponseEntity<ProjetoDTO> criar(@AuthenticationPrincipal @NonNull CustomUserDetails user,
            @RequestBody @Valid @NonNull ProjetoCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projetoService.criarProjeto(user.getId(), dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca os detalhes de um projeto específico")
    public ResponseEntity<ProjetoDTO> buscarPorId(@AuthenticationPrincipal @NonNull CustomUserDetails user,
            @PathVariable @NonNull UUID id) {
        return ResponseEntity.ok(projetoService.buscarPorId(user.getId(), id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Altera o status (etapa) do projeto")
    public ResponseEntity<ProjetoDTO> atualizarStatus(@AuthenticationPrincipal @NonNull CustomUserDetails user,
            @PathVariable @NonNull UUID id,
            @RequestBody @Valid @NonNull StatusProjetoUpdateDTO dto) {
        return ResponseEntity
                .ok(projetoService.atualizarStatus(user.getId(), id, Objects.requireNonNull(dto.status())));
    }

    @PostMapping("/{id}/revogar-link")
    @Operation(summary = "Revoga (desativa) o Magic Link do projeto")
    public ResponseEntity<Void> revogarMagicLink(@AuthenticationPrincipal @NonNull CustomUserDetails user,
            @PathVariable @NonNull UUID id) {
        projetoService.revogarMagicLink(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
