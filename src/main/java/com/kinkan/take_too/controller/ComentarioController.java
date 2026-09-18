package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.ComentarioCreateDTO;
import com.kinkan.take_too.domain.dto.ComentarioDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.ComentarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/versoes/{versaoId}/comentarios")
@RequiredArgsConstructor
@Tag(name = "Comentários", description = "Endpoints para criar e listar anotações de uma versão")
public class ComentarioController {

    private final ComentarioService comentarioService;

    @GetMapping
    @Operation(summary = "Lista todos os comentários atrelados a uma versão")
    public ResponseEntity<List<ComentarioDTO>> listar(@AuthenticationPrincipal CustomUserDetails user,
                                                      @PathVariable UUID versaoId) {
        return ResponseEntity.ok(comentarioService.listarComentariosDaVersao(user.getId(), versaoId));
    }

    @PostMapping
    @Operation(summary = "Adiciona um comentário a uma versão de projeto")
    public ResponseEntity<ComentarioDTO> criar(@AuthenticationPrincipal CustomUserDetails user,
                                               @PathVariable UUID versaoId,
                                               @RequestBody @Valid ComentarioCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comentarioService.criarComentario(user.getId(), versaoId, dto));
    }
}
