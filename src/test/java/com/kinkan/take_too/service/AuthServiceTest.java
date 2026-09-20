package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.LoginRequestDTO;
import com.kinkan.take_too.domain.dto.RegisterRequestDTO;
import com.kinkan.take_too.domain.dto.TokenResponseDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.entity.RefreshToken;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.exception.UnauthorizedException;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private ProjetoRepository projetoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenService tokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private UUID profissionalId;
    private UUID projetoId;
    private Profissional profissional;
    private Cliente cliente;
    private Projeto projeto;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        profissionalId = UUID.randomUUID();
        projetoId = UUID.randomUUID();

        profissional = new Profissional();
        profissional.setId(profissionalId);
        profissional.setNome("Videomaker");
        profissional.setEmail("videomaker@teste.com");
        profissional.setSenhaHash("hash");
        profissional.setPlano("FREE");

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Cliente Teste");

        projeto = new Projeto();
        projeto.setId(projetoId);
        projeto.setProfissional(profissional);
        projeto.setCliente(cliente);
        projeto.setMagicLinkAtivo(true);

        refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setToken("mock-refresh-token");
        refreshToken.setProfissional(profissional);
        refreshToken.setDataExpiracao(Instant.now().plusSeconds(3600));
        refreshToken.setRevogado(false);
    }

    @Test
    void deveFazerLoginComSucesso() {
        LoginRequestDTO dto = new LoginRequestDTO("videomaker@teste.com", "senha123");
        CustomUserDetails userDetails = new CustomUserDetails(profissional);
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(profissionalRepository.findById(Objects.requireNonNull(profissionalId))).thenReturn(Optional.of(profissional));
        when(tokenService.generateToken(profissional)).thenReturn("jwt-token");
        when(refreshTokenService.createRefreshToken(profissional)).thenReturn(refreshToken);

        TokenResponseDTO response = authService.login(dto);

        assertNotNull(response);
        assertEquals("jwt-token", response.token());
        assertEquals("mock-refresh-token", response.refreshToken());
    }

    @Test
    void deveRegistrarProfissionalComSucesso() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Novo Videomaker", "novo@teste.com", "senha123", "FREE");

        when(profissionalRepository.findByEmail(dto.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(dto.senha())).thenReturn("senhaEncoded");
        when(tokenService.generateToken(any(Profissional.class))).thenReturn("token-registrado");
        when(refreshTokenService.createRefreshToken(any(Profissional.class))).thenReturn(refreshToken);

        TokenResponseDTO response = authService.register(dto);

        assertNotNull(response);
        assertEquals("token-registrado", response.token());
        assertEquals("mock-refresh-token", response.refreshToken());
        verify(profissionalRepository, times(1)).save(any(Profissional.class));
    }

    @Test
    void deveLancarExcecaoAoRegistrarEmailJaExistente() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Novo Videomaker", "existente@teste.com", "senha123", "FREE");

        when(profissionalRepository.findByEmail(dto.email())).thenReturn(Optional.of(profissional));

        assertThrows(IllegalArgumentException.class, () -> authService.register(dto));
        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void deveRenovarTokenComSucesso() {
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken("novo-refresh-token");
        newRefreshToken.setProfissional(profissional);

        when(refreshTokenService.findByToken("mock-refresh-token")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenService.rotateRefreshToken(refreshToken)).thenReturn(newRefreshToken);
        when(tokenService.generateToken(profissional)).thenReturn("novo-jwt-token");

        TokenResponseDTO response = authService.refresh("mock-refresh-token");

        assertNotNull(response);
        assertEquals("novo-jwt-token", response.token());
        assertEquals("novo-refresh-token", response.refreshToken());
    }

    @Test
    void deveLancarExcecaoAoRenovarComTokenNuloOuVazio() {
        assertThrows(UnauthorizedException.class, () -> authService.refresh(null));
        assertThrows(UnauthorizedException.class, () -> authService.refresh("   "));
    }

    @Test
    void deveLancarExcecaoAoRenovarComTokenNaoEncontrado() {
        when(refreshTokenService.findByToken("inexistente")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refresh("inexistente"));
    }

    @Test
    void deveFazerLogoutRevogandoToken() {
        authService.logout("mock-refresh-token");

        verify(refreshTokenService, times(1)).revokeToken("mock-refresh-token");
    }

    @Test
    void deveGerarMagicLinkComSucessoQuandoProfissionalEODono() {
        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));
        when(tokenService.generateClientToken(cliente, projetoId)).thenReturn("client-jwt-token");

        TokenResponseDTO response = authService.generateMagicLinkToken(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId));

        assertNotNull(response);
        assertEquals("client-jwt-token", response.token());
    }

    @Test
    void deveLancarExcecaoAoGerarMagicLinkParaProjetoDeOutroProfissional() {
        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                authService.generateMagicLinkToken(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId)));

        verify(tokenService, never()).generateClientToken(any(), any());
    }

    @Test
    void deveReativarMagicLinkEGerarTokenSeEstiverInativo() {
        projeto.setMagicLinkAtivo(false);

        when(projetoRepository.findByIdAndProfissional_Id(Objects.requireNonNull(projetoId), Objects.requireNonNull(profissionalId)))
                .thenReturn(Optional.of(projeto));
        when(tokenService.generateClientToken(cliente, projetoId)).thenReturn("novo-client-token");

        TokenResponseDTO response = authService.generateMagicLinkToken(Objects.requireNonNull(profissionalId), Objects.requireNonNull(projetoId));

        assertNotNull(response);
        assertEquals("novo-client-token", response.token());
        assertTrue(projeto.isMagicLinkAtivo());
        verify(projetoRepository).save(projeto);
        verify(tokenService).generateClientToken(cliente, projetoId);
    }
}
