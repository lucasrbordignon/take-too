package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.Map;

public record ImagemUploadDTO(ImagemDTO imagem, String url, String metodo, Map<String, String> headers, Instant expiraEm) {}
