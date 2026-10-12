package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.VersaoCreateDTO;
import com.kinkan.take_too.domain.dto.VersaoDTO;
import com.kinkan.take_too.domain.dto.VideoPlaybackDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.VersaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projetos/{projetoId}/versoes")
@RequiredArgsConstructor
@Tag(name = "Versões", description = "Endpoints para criar e listar as versões de vídeo de um projeto")
public class VersaoController {

    private final VersaoService versaoService;

    @GetMapping
    @Operation(summary = "Lista todas as versões atreladas a um projeto")
    public ResponseEntity<List<VersaoDTO>> listar(@AuthenticationPrincipal CustomUserDetails user,
                                                  @PathVariable UUID projetoId) {
        return ResponseEntity.ok(versaoService.listarVersoesDoProjeto(user.getId(), projetoId));
    }

    @PostMapping
    @Operation(summary = "Publica uma versão por videoId pronto ou arquivoUrl legado e inicia revisão")
    public ResponseEntity<VersaoDTO> criar(@AuthenticationPrincipal CustomUserDetails user,
                                           @PathVariable UUID projetoId,
                                           @RequestBody @Valid VersaoCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(versaoService.criarVersao(user.getId(), projetoId, dto));
    }

    @PostMapping("/{versaoId}/playback")
    @Operation(summary = "Gera reprodução privada do vídeo da versão para o profissional dono")
    public ResponseEntity<VideoPlaybackDTO> playback(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID projetoId, @PathVariable UUID versaoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(versaoService.criarPlayback(user.getId(), projetoId, versaoId));
    }
}
