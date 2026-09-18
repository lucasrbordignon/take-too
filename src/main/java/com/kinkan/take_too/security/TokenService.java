package com.kinkan.take_too.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty() || secret.trim().length() < 32) {
            throw new IllegalStateException("Configuração insegura: a propriedade api.security.token.secret (JWT_SECRET) deve ser definida e possuir no mínimo 32 caracteres.");
        }
    }

    public String generateToken(Profissional profissional) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("take-too-api")
                    .withSubject(profissional.getEmail())
                    .withClaim("role", "ROLE_PROFISSIONAL")
                    .withExpiresAt(generateExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Error while generating token", exception);
        }
    }

    public String generateClientToken(Cliente cliente, UUID projetoId) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("take-too-api")
                    .withSubject(cliente.getId().toString())
                    .withClaim("role", "ROLE_CLIENTE")
                    .withClaim("projetoId", projetoId.toString())
                    .withExpiresAt(generateClientExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Error while generating client token", exception);
        }
    }

    public String validateToken(String token) {
        DecodedJWT jwt = decodeToken(token);
        return jwt != null ? jwt.getSubject() : "";
    }

    public DecodedJWT decodeToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("take-too-api")
                    .build()
                    .verify(token);
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    private Instant generateExpirationDate() {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }

    private Instant generateClientExpirationDate() {
        return LocalDateTime.now().plusDays(7).toInstant(ZoneOffset.of("-03:00")); // Token do cliente dura 7 dias
    }
}
