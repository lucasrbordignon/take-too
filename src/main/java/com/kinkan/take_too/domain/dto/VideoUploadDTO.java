package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import java.util.Map;

public record VideoUploadDTO(VideoDTO video, String url, String metodo,
                             String protocolo, Map<String, String> headers, Instant expiraEm) {}
