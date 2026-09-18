package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.ComentarioCreateDTO;
import com.kinkan.take_too.domain.dto.ComentarioDTO;
import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.dto.StatusVersaoUpdateDTO;
import com.kinkan.take_too.domain.dto.VersaoDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.PortalClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente", description = "Endpoints de acesso público restrito via Magic Link (Role: CLIENTE)")
public class PortalClienteController {

    private final PortalClienteService portalClienteService;

    @GetMapping("/projetos/{projetoId}")
    @Operation(summary = "Retorna os detalhes do projeto para o cliente")
    public ResponseEntity<ProjetoDTO> buscarProjeto(@AuthenticationPrincipal CustomUserDetails user,
            @NonNull @PathVariable UUID projetoId) {
        // Validação adicional de role pode ser feita via SecurityConfig ou Filter
        return ResponseEntity
                .ok(portalClienteService.buscarProjeto(Objects.requireNonNull(user.getProjetoIdAcesso()), projetoId));
    }

    @GetMapping("/projetos/{projetoId}/versoes")
    @Operation(summary = "Lista as versões de um projeto para o cliente")
    public ResponseEntity<List<VersaoDTO>> listarVersoes(@AuthenticationPrincipal CustomUserDetails user,
            @NonNull @PathVariable UUID projetoId) {
        return ResponseEntity
                .ok(portalClienteService.listarVersoes(Objects.requireNonNull(user.getProjetoIdAcesso()), projetoId));
    }

    @PostMapping("/versoes/{versaoId}/comentarios")
    @Operation(summary = "Permite ao cliente adicionar um comentário na versão")
    public ResponseEntity<ComentarioDTO> criarComentario(@AuthenticationPrincipal CustomUserDetails user,
            @NonNull @PathVariable UUID versaoId,
            @RequestBody @Valid ComentarioCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portalClienteService.criarComentario(user.getId(),
                        Objects.requireNonNull(user.getProjetoIdAcesso()), versaoId, Objects.requireNonNull(dto)));
    }

    @PatchMapping("/versoes/{versaoId}/status")
    @Operation(summary = "Permite ao cliente Aprovar ou Rejeitar a versão")
    public ResponseEntity<Void> alterarStatusVersao(@AuthenticationPrincipal CustomUserDetails user,
            @NonNull @PathVariable UUID versaoId,
            @RequestBody @Valid StatusVersaoUpdateDTO dto) {
        portalClienteService.alterarStatusVersao(Objects.requireNonNull(user.getProjetoIdAcesso()), versaoId,
                Objects.requireNonNull(dto.status()));
        return ResponseEntity.noContent().build();
    }
}
