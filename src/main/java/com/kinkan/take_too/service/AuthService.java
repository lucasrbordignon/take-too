package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.LoginRequestDTO;
import com.kinkan.take_too.domain.dto.RegisterRequestDTO;
import com.kinkan.take_too.domain.dto.TokenResponseDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import com.kinkan.take_too.security.CustomUserDetails;
import com.kinkan.take_too.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ProfissionalRepository profissionalRepository;
    private final ProjetoRepository projetoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    @org.springframework.transaction.annotation.Transactional
    public TokenResponseDTO login(LoginRequestDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.senha());
        var auth = this.authenticationManager.authenticate(usernamePassword);

        var userDetails = (CustomUserDetails) auth.getPrincipal();
        var profissional = profissionalRepository.findById(userDetails.getId()).orElseThrow();

        String accessToken = tokenService.generateToken(profissional);
        var refreshToken = refreshTokenService.createRefreshToken(profissional);

        return new TokenResponseDTO(accessToken, refreshToken.getToken());
    }

    @org.springframework.transaction.annotation.Transactional
    public TokenResponseDTO register(RegisterRequestDTO dto) {
        if (profissionalRepository.findByEmail(dto.email()).isPresent()) {
            throw new IllegalArgumentException("E-mail já está em uso.");
        }

        Profissional profissional = new Profissional();
        profissional.setNome(dto.nome());
        profissional.setEmail(dto.email());
        profissional.setSenhaHash(passwordEncoder.encode(dto.senha()));
        profissional.setPlano("FREE");

        profissionalRepository.save(profissional);

        String token = tokenService.generateToken(profissional);
        var refreshToken = refreshTokenService.createRefreshToken(profissional);
        return new TokenResponseDTO(token, refreshToken.getToken());
    }

    @org.springframework.transaction.annotation.Transactional
    public TokenResponseDTO refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.trim().isEmpty()) {
            throw new com.kinkan.take_too.exception.UnauthorizedException("Refresh token não informado.");
        }

        var refreshToken = refreshTokenService.findByToken(refreshTokenValue.trim())
                .orElseThrow(() -> new com.kinkan.take_too.exception.UnauthorizedException("Refresh token inválido ou não encontrado."));

        var newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);
        String newAccessToken = tokenService.generateToken(newRefreshToken.getProfissional());

        return new TokenResponseDTO(newAccessToken, newRefreshToken.getToken());
    }

    @org.springframework.transaction.annotation.Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.trim().isEmpty()) {
            refreshTokenService.revokeToken(refreshTokenValue.trim());
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public TokenResponseDTO generateMagicLinkToken(@org.springframework.lang.NonNull UUID profissionalId, @org.springframework.lang.NonNull UUID projetoId) {
        Projeto projeto = projetoRepository.findByIdAndProfissional_Id(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));

        if (!projeto.isMagicLinkAtivo()) {
            projeto.setMagicLinkAtivo(true);
            projetoRepository.save(projeto);
        }

        Cliente cliente = projeto.getCliente();
        String token = tokenService.generateClientToken(cliente, projetoId);

        return new TokenResponseDTO(token);
    }
}
