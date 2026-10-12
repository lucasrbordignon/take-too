package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.VideoDTO;
import com.kinkan.take_too.domain.dto.VideoUploadCreateDTO;
import com.kinkan.take_too.domain.dto.VideoUploadDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projetos/{projetoId}/videos")
@RequiredArgsConstructor
@Tag(name = "Vídeos do projeto", description = "Rascunhos de upload; publicar é uma ação separada")
public class ProjetoVideoController {
    private final VideoService service;

    @PostMapping("/uploads")
    @Operation(summary = "Cria upload em rascunho no projeto em edição, sem publicar versão")
    public ResponseEntity<VideoUploadDTO> criar(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID projetoId, @Valid @RequestBody VideoUploadCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.criarUploadNoProjeto(user.getId(), projetoId, dto));
    }

    @GetMapping
    @Operation(summary = "Lista rascunhos de vídeo do projeto para continuar o envio ou publicar")
    public ResponseEntity<Page<VideoDTO>> listar(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID projetoId, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listarRascunhos(user.getId(), projetoId, pagina, tamanho));
    }

    @DeleteMapping("/{videoId}")
    @Operation(summary = "Retira o rascunho do projeto, mantendo o vídeo na biblioteca")
    public ResponseEntity<Void> desvincular(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID projetoId, @PathVariable UUID videoId) {
        service.desvincularRascunho(user.getId(), projetoId, videoId);
        return ResponseEntity.noContent().build();
    }
}
