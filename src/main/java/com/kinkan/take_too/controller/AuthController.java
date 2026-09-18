package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.LoginRequestDTO;
import com.kinkan.take_too.domain.dto.auth.MagicLinkRequestDTO;
import com.kinkan.take_too.domain.dto.RegisterRequestDTO;
import com.kinkan.take_too.domain.dto.TokenResponseDTO;
import com.kinkan.take_too.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints para registro e login do profissional")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Realiza o login e retorna o token JWT")
    public ResponseEntity<TokenResponseDTO> login(@RequestBody @Valid LoginRequestDTO data) {
        var token = authService.login(data);
        return ResponseEntity.ok(new TokenResponseDTO(token));
    }

    @PostMapping("/register")
    @Operation(summary = "Registra um novo profissional")
    public ResponseEntity<TokenResponseDTO> register(@RequestBody @Valid RegisterRequestDTO dto) {
        return ResponseEntity.ok(authService.register(dto));
    }

    @PostMapping("/magic-link")
    @Operation(summary = "Gera um token de acesso para o Cliente Final a partir do ID do Projeto")
    public ResponseEntity<TokenResponseDTO> magicLink(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.kinkan.take_too.security.CustomUserDetails user,
            @RequestBody @Valid MagicLinkRequestDTO dto) {
        return ResponseEntity.ok(authService.generateMagicLinkToken(Objects.requireNonNull(user.getId()), Objects.requireNonNull(dto.projetoId())));
    }
}
