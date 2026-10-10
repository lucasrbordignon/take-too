package com.kinkan.take_too.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.video")
@Getter
@Setter
public class VideoProperties {
    private boolean enabled = false;
    private String provider = "mux";
    private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:5173");
    private List<String> watermarkAllowedHosts = List.of();
    private int playbackTtlSeconds = 3600;
}
