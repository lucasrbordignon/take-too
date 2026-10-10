package com.kinkan.take_too.service.video;

import com.kinkan.take_too.exception.VideoProviderException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class VideoProviderRegistry {
    private final Map<String, VideoProvider> providers;

    public VideoProviderRegistry(List<VideoProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(VideoProvider::id, Function.identity()));
    }

    public VideoProvider obter(String id) {
        VideoProvider provider = providers.get(id);
        if (provider == null) {
            throw new VideoProviderException("Provedor de vídeo não configurado.");
        }
        return provider;
    }
}
