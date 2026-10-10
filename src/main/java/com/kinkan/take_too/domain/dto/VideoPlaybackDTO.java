package com.kinkan.take_too.domain.dto;

import java.time.Instant;

public record VideoPlaybackDTO(VideoDTO video, String url, String tipo, Instant expiraEm) {}
