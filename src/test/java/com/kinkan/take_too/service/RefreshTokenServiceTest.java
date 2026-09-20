package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.RefreshToken;
import com.kinkan.take_too.exception.UnauthorizedException;
import com.kinkan.take_too.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Profissional profissional;

    @BeforeEach
    void setUp() {
        profissional = new Profissional();
        profissional.setId(UUID.randomUUID());
        profissional.setNome("Videomaker");
        profissional.setEmail("videomaker@teste.com");
    }

    @Test
    void deveCriarRefreshTokenComSucesso() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RefreshToken token = refreshTokenService.createRefreshToken(profissional);

        assertNotNull(token);
        assertNotNull(token.getToken());
        assertFalse(token.isRevogado());
        assertTrue(token.getDataExpiracao().isAfter(Instant.now()));
        assertEquals(profissional, token.getProfissional());
    }

    @Test
    void deveVerificarTokenValidoSemLancarExcecao() {
        RefreshToken token = new RefreshToken();
        token.setRevogado(false);
        token.setDataExpiracao(Instant.now().plus(1, ChronoUnit.DAYS));

        assertDoesNotThrow(() -> refreshTokenService.verify(token));
    }

    @Test
    void deveLancarExcecaoQuandoTokenEstiverRevogado() {
        RefreshToken token = new RefreshToken();
        token.setRevogado(true);
        token.setDataExpiracao(Instant.now().plus(1, ChronoUnit.DAYS));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.verify(token));
    }

    @Test
    void deveLancarExcecaoQuandoTokenEstiverExpirado() {
        RefreshToken token = new RefreshToken();
        token.setRevogado(false);
        token.setDataExpiracao(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.verify(token));
    }

    @Test
    void deveRotacionarTokenComSucesso() {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setId(UUID.randomUUID());
        oldToken.setToken("old-token");
        oldToken.setProfissional(profissional);
        oldToken.setRevogado(false);
        oldToken.setDataExpiracao(Instant.now().plus(1, ChronoUnit.DAYS));

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken newToken = refreshTokenService.rotateRefreshToken(oldToken);

        assertNotNull(newToken);
        assertNotEquals("old-token", newToken.getToken());
        assertTrue(oldToken.isRevogado());
        assertFalse(newToken.isRevogado());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void deveRevogarTokenPorString() {
        RefreshToken token = new RefreshToken();
        token.setToken("token-to-revoke");
        token.setRevogado(false);

        when(refreshTokenRepository.findByToken("token-to-revoke")).thenReturn(Optional.of(token));

        refreshTokenService.revokeToken("token-to-revoke");

        assertTrue(token.isRevogado());
        verify(refreshTokenRepository).save(token);
    }
}
