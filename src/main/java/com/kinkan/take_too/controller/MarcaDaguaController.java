package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.ImagemService;
import com.kinkan.take_too.service.MarcaDaguaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profissionais/me/marca-dagua")
@RequiredArgsConstructor
@Tag(name = "Marca d'água", description = "Preferências privadas do profissional; aplicação no processamento é uma etapa separada")
public class MarcaDaguaController {
    private final MarcaDaguaService service;
    private final ImagemService imagens;

    @GetMapping
    @Operation(summary = "Consulta preferências de marca d'água ou valores iniciais desativados")
    public ResponseEntity<MarcaDaguaPreferenciasDTO> buscar(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscar(user.getId()));
    }

    @PutMapping
    @Operation(summary = "Substitui preferências e captura alterações somente para próximas mídias")
    public ResponseEntity<MarcaDaguaPreferenciasDTO> salvar(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody @Valid MarcaDaguaPreferenciasUpdateDTO dto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.salvar(user.getId(), dto));
    }

    @PostMapping("/logo/uploads")
    @Operation(summary = "Autoriza upload privado de logo; confirme o arquivo antes de vinculá-lo às preferências")
    public ResponseEntity<ImagemUploadDTO> upload(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody @Valid ImagemUploadCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(imagens.criarUploadLogo(user.getId(), dto));
    }
}
