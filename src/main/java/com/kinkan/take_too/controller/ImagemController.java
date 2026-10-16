package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.ImagemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Imagens", description = "Biblioteca privada, uploads em rascunho e publicação separada")
public class ImagemController {
    private final ImagemService service;
    @PostMapping("/api/imagens/uploads")
    @Operation(summary = "Autoriza upload direto de imagem, sem publicar versão")
    public ResponseEntity<ImagemUploadDTO> criar(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody ImagemUploadCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.criarUpload(user.getId(), null, dto));
    }
    @PostMapping("/api/projetos/{projetoId}/imagens/uploads")
    @Operation(summary = "Autoriza upload de imagem em rascunho no projeto em edição")
    public ResponseEntity<ImagemUploadDTO> criarNoProjeto(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID projetoId, @Valid @RequestBody ImagemUploadCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.criarUpload(user.getId(), projetoId, dto));
    }
    @GetMapping("/api/imagens")
    @Operation(summary = "Lista biblioteca de imagens do profissional")
    public ResponseEntity<Page<ImagemDTO>> listar(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar(user.getId(), null, pagina, tamanho));
    }
    @GetMapping("/api/projetos/{projetoId}/imagens")
    @Operation(summary = "Lista rascunhos de imagens do projeto")
    public ResponseEntity<Page<ImagemDTO>> listarRascunhos(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID projetoId, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar(user.getId(), projetoId, pagina, tamanho));
    }
    @GetMapping("/api/imagens/{id}/upload")
    @Operation(summary = "Renova autorização de envio de uma imagem pendente, sem criar novo registro")
    public ResponseEntity<ImagemUploadDTO> upload(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscarUpload(user.getId(), id));
    }
    @PostMapping("/api/imagens/{id}/confirmar")
    @Operation(summary = "Confere arquivo, checksum, tamanho e dimensões antes de liberar publicação")
    public ResponseEntity<ImagemDTO> confirmar(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.confirmar(user.getId(), id));
    }
    @PostMapping("/api/imagens/{id}/visualizacao")
    @Operation(summary = "Emite visualização temporária da imagem privada do profissional")
    public ResponseEntity<ImagemVisualizacaoDTO> visualizar(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.visualizar(user.getId(), id));
    }
    @DeleteMapping("/api/imagens/{id}")
    @Operation(summary = "Exclui imagem não publicada; preserva imagens vinculadas ao histórico")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        service.excluir(user.getId(), id); return ResponseEntity.noContent().build();
    }
}
