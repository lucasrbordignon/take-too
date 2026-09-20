package com.kinkan.take_too.domain.dto;

public record TokenResponseDTO(
        String token,
        String refreshToken
) {
    public TokenResponseDTO(String token) {
        this(token, null);
    }
}
