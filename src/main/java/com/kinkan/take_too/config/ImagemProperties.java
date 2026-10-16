package com.kinkan.take_too.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.imagem")
@Getter
@Setter
public class ImagemProperties {
    private boolean enabled;
    private String provider = "s3";
    private long maxBytes = 10 * 1024 * 1024;
    private long maxPixels = 25_000_000;
    private String bucket = "";
    private String region = "us-east-1";
    private String endpoint = "";
    private boolean pathStyle;
}
