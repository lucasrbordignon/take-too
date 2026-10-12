package com.kinkan.take_too.domain.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record VideoPlaybackDTO(VideoDTO video, String url, String tipo, Instant expiraEm,
                               @Nullable String miniaturaUrl) {}
