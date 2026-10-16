package com.kinkan.take_too.service.storage;

import com.kinkan.take_too.exception.ArmazenamentoException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ImageStorageRegistry {
    private final Map<String, ImageStorageProvider> providers;
    public ImageStorageRegistry(List<ImageStorageProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(ImageStorageProvider::id, Function.identity()));
    }
    public ImageStorageProvider obter(String id) {
        var provider = providers.get(id);
        if (provider == null) throw new ArmazenamentoException("Armazenamento de imagens não configurado.");
        return provider;
    }
}
