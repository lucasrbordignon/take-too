package com.kinkan.take_too.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kinkan.take_too.config.VideoProperties;
import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.domain.entity.ImagemAsset;
import com.kinkan.take_too.domain.enums.StatusImagem;
import com.kinkan.take_too.exception.ArmazenamentoException;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarcaDaguaServiceTest {
    private final ImagemAssetRepository imagens = mock(ImagemAssetRepository.class);
    private final ImageStorageProvider provider = mock(ImageStorageProvider.class);
    private final VideoProperties properties = new VideoProperties();
    private final UUID profissionalId = UUID.randomUUID();
    private final UUID logoId = UUID.randomUUID();
    private MarcaDaguaService service;
    private MarcaDaguaSnapshotDTO snapshot;

    @BeforeEach void preparar() {
        when(provider.id()).thenReturn("fake"); properties.setWatermarkAllowedHosts(List.of("storage.example.test"));
        service = new MarcaDaguaService(mock(ProfissionalRepository.class), mock(PreferenciasMarcaDaguaRepository.class),
                imagens, new ImageStorageRegistry(List.of(provider)), properties, JsonMapper.builder().findAndAddModules().build());
        var logo = new ImagemAsset(); logo.setId(logoId); logo.setProvedor("fake"); logo.setChave("imagens/logo.png");
        logo.setTipo("image/png"); logo.setTamanhoBytes(100); logo.setLargura(64); logo.setAltura(32); logo.setStatus(StatusImagem.PRONTA);
        when(imagens.findByIdAndProfissional_Id(logoId,profissionalId)).thenReturn(Optional.of(logo));
        snapshot = new MarcaDaguaSnapshotDTO(logoId,MarcaDaguaDTO.Posicao.CENTRO,20,70,3,Instant.now());
    }
    @Test void deveEmitirUrlDeProcessamentoComValidadeDe48Horas() {
        when(provider.visualizar(eq("imagens/logo.png"),any())).thenReturn("https://storage.example.test/logo.png?assinatura=temporaria");
        Instant before = Instant.now(); var result = service.prepararParaProcessamento(profissionalId,snapshot);
        var expiry = ArgumentCaptor.forClass(Instant.class); verify(provider).visualizar(eq("imagens/logo.png"),expiry.capture());
        assertFalse(expiry.getValue().isBefore(before.plusSeconds(172800)));
        assertFalse(expiry.getValue().isAfter(Instant.now().plusSeconds(172800)));
        assertEquals(20,result.larguraPercentual()); assertEquals(MarcaDaguaDTO.Posicao.CENTRO,result.posicao());
    }
    @Test void deveRejeitarUrlsDeProcessamentoInsegurasOuHostsNaoPermitidos() {
        for (String url : List.of("http://storage.example.test/logo.png", "https://evil.test/logo.png",
                "https://user@storage.example.test/logo.png", "https://storage.example.test:8443/logo.png",
                "https://storage.example.test/logo.png#fragmento")) {
            when(provider.visualizar(anyString(),any())).thenReturn(url);
            assertThrows(ArmazenamentoException.class,()->service.prepararParaProcessamento(profissionalId,snapshot));
        }
    }
}
