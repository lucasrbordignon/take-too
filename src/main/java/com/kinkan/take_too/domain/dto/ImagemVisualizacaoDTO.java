package com.kinkan.take_too.domain.dto;

import java.time.Instant;

public record ImagemVisualizacaoDTO(ImagemDTO imagem, String url, Instant expiraEm) {}
