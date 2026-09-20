package com.kinkan.take_too.controller;

import com.kinkan.take_too.domain.dto.LoginRequestDTO;
import com.kinkan.take_too.domain.dto.RegisterRequestDTO;
import com.kinkan.take_too.domain.dto.TokenResponseDTO;
import com.kinkan.take_too.domain.dto.auth.MagicLinkRequestDTO;
import com.kinkan.take_too.domain.dto.auth.RefreshTokenRequestDTO;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints para registro, login e renovação de tokens do profissional")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Realiza o login e retorna os tokens de acesso e refresh")
    public ResponseEntity<TokenResponseDTO> login(@RequestBody @Valid LoginRequestDTO data) {
        TokenResponseDTO tokens = authService.login(data);
        ResponseCookie cookie = buildRefreshTokenCookie(tokens.refreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokens);
    }

    @PostMapping("/register")
    @Operation(summary = "Registra um novo profissional")
    public ResponseEntity<TokenResponseDTO> register(@RequestBody @Valid RegisterRequestDTO dto) {
        TokenResponseDTO tokens = authService.register(dto);
        ResponseCookie cookie = buildRefreshTokenCookie(tokens.refreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokens);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o Access Token utilizando o Refresh Token (via Cookie ou Body)")
    public ResponseEntity<TokenResponseDTO> refresh(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequestDTO body) {

        String refreshTokenValue = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : (body != null ? body.refreshToken() : null);

        TokenResponseDTO newTokens = authService.refresh(refreshTokenValue);
        ResponseCookie cookie = buildRefreshTokenCookie(newTokens.refreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(newTokens);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoga o Refresh Token e remove o cookie de autenticação")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequestDTO body) {

        String refreshTokenValue = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : (body != null ? body.refreshToken() : null);

        authService.logout(refreshTokenValue);
        ResponseCookie cookie = buildRefreshTokenCookie("", Duration.ZERO);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @PostMapping("/magic-link")
    @Operation(summary = "Gera um token de acesso para o Cliente Final a partir do ID do Projeto")
    public ResponseEntity<TokenResponseDTO> magicLink(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody @Valid MagicLinkRequestDTO dto) {
        return ResponseEntity.ok(authService.generateMagicLinkToken(
                Objects.requireNonNull(user.getId()),
                Objects.requireNonNull(dto.projetoId())));
    }

    private ResponseCookie buildRefreshTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from("refreshToken", token != null ? token : "")
                .httpOnly(true)
                .secure(false) // Permite http://localhost em desenvolvimento.
                .path("/api/auth")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
