package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.RefreshToken;
import com.kinkan.take_too.exception.UnauthorizedException;
import com.kinkan.take_too.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Duration REFRESH_TOKEN_DURATION = Duration.ofDays(7);
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public RefreshToken createRefreshToken(@NonNull Profissional profissional) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setProfissional(profissional);
        refreshToken.setToken(generateSecureToken());
        refreshToken.setDataExpiracao(Instant.now().plus(REFRESH_TOKEN_DURATION));
        refreshToken.setRevogado(false);
        refreshToken.setCriadoEm(Instant.now());

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByToken(token.trim());
    }

    public void verify(@NonNull RefreshToken token) {
        if (token.isRevogado()) {
            throw new UnauthorizedException("Refresh token foi revogado.");
        }
        if (token.getDataExpiracao().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expirou.");
        }
    }

    @Transactional
    public RefreshToken rotateRefreshToken(@NonNull RefreshToken oldToken) {
        verify(oldToken);

        // Invalida o token anterior para prevenir replay attacks
        oldToken.setRevogado(true);
        refreshTokenRepository.save(oldToken);

        // Emite um novo token para o mesmo profissional
        return createRefreshToken(oldToken.getProfissional());
    }

    @Transactional
    public void revokeToken(String tokenString) {
        findByToken(tokenString).ifPresent(token -> {
            token.setRevogado(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void revokeAllFromProfissional(@NonNull UUID profissionalId) {
        refreshTokenRepository.revogarTodosDoProfissional(profissionalId);
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
