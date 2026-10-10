package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.*;
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
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Tag(name = "Vídeos independentes", description = "Upload e streaming sem alterar projetos ou versões")
public class VideoController {
    private final VideoService service;

    @PostMapping("/uploads")
    @Operation(summary = "Cria um vídeo independente e obtém uma sessão de upload direto")
    public ResponseEntity<VideoUploadDTO> criar(@AuthenticationPrincipal CustomUserDetails user,
                                               @RequestBody @Valid VideoUploadCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.criarUpload(user.getId(), dto));
    }

    @GetMapping
    @Operation(summary = "Lista os vídeos do profissional com paginação")
    public ResponseEntity<Page<VideoDTO>> listar(@AuthenticationPrincipal CustomUserDetails user,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar(user.getId(), pagina, tamanho));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta estado e metadados locais do vídeo")
    public ResponseEntity<VideoDTO> buscar(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscar(user.getId(), id));
    }

    @PostMapping("/{id}/sincronizar")
    @Operation(summary = "Atualiza o vídeo pelo estado atual no provedor, mesmo sem webhook")
    public ResponseEntity<VideoDTO> sincronizar(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.sincronizar(user.getId(), id));
    }

    @GetMapping("/{id}/upload")
    @Operation(summary = "Recupera a sessão de upload ativa para retomar o envio")
    public ResponseEntity<VideoUploadDTO> upload(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscarUpload(user.getId(), id));
    }

    @PostMapping("/{id}/playback")
    @Operation(summary = "Gera uma sessão temporária de reprodução HLS privada")
    public ResponseEntity<VideoPlaybackDTO> playback(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.criarPlayback(user.getId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancela o upload ou exclui o vídeo no provedor e mantém um registro local de exclusão")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        service.excluir(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
