package com.kinkan.take_too.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.video.mux")
@Getter
@Setter
public class MuxProperties {
    private String tokenId = "";
    private String tokenSecret = "";
    private String webhookSecret = "";
    private String signingKeyId = "";
    private String signingPrivateKey = "";
    private int uploadTimeoutSeconds = 3600;
}
