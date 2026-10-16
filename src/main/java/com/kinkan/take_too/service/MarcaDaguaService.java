package com.kinkan.take_too.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinkan.take_too.config.VideoProperties;
import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.StatusImagem;
import com.kinkan.take_too.exception.*;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.storage.ImageStorageRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarcaDaguaService {
    public static final long MAX_LOGO_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LOGO_DIMENSAO = 4096;
    private final ProfissionalRepository profissionais;
    private final PreferenciasMarcaDaguaRepository preferencias;
    private final ImagemAssetRepository imagens;
    private final ImageStorageRegistry storage;
    private final VideoProperties videoProperties;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public MarcaDaguaPreferenciasDTO buscar(UUID profissionalId) {
        if (!profissionais.existsById(profissionalId)) throw new ResourceNotFoundException("Profissional não encontrado.");
        return preferencias.findById(profissionalId).map(this::toDTO).orElseGet(() ->
                new MarcaDaguaPreferenciasDTO(false, null, null, MarcaDaguaDTO.Posicao.INFERIOR_DIREITA, 15, 70, 3, null));
    }

    @Transactional
    public MarcaDaguaPreferenciasDTO salvar(UUID profissionalId, MarcaDaguaPreferenciasUpdateDTO dto) {
        validar(dto);
        // Serializa também a primeira configuração, quando a linha de preferências ainda não existe.
        var profissional = profissionais.findByIdForUpdate(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado."));
        ImagemAsset logo = dto.logoImagemId() == null ? null : logo(profissionalId, dto.logoImagemId(), true);
        if (dto.ativaPadrao() && logo == null) throw new IllegalArgumentException("Selecione uma logo pronta antes de ativar a marca d'água.");
        var config = preferencias.findById(profissionalId).orElseGet(PreferenciasMarcaDagua::new);
        config.setProfissional(profissional);
        config.setLogo(logo); config.setAtivaPadrao(dto.ativaPadrao()); config.setPosicao(dto.posicao());
        config.setLarguraPercentual(dto.larguraPercentual()); config.setOpacidadePercentual(dto.opacidadePercentual());
        config.setMargemPercentual(dto.margemPercentual()); config.setAtualizadoEm(Instant.now());
        return toDTO(preferencias.save(config));
    }

    @Transactional
    public @Nullable Captura capturar(UUID profissionalId) {
        profissionais.findByIdForUpdate(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado."));
        var config = preferencias.findById(profissionalId).orElse(null);
        if (config == null || !config.isAtivaPadrao()) return null;
        if (config.getLogo() == null) throw new IllegalStateException("Marca d'água ativa sem logo configurada.");
        var logo = logo(profissionalId, config.getLogo().getId(), true);
        var snapshot = new MarcaDaguaSnapshotDTO(logo.getId(), config.getPosicao(), config.getLarguraPercentual(),
                config.getOpacidadePercentual(), config.getMargemPercentual(), Instant.now());
        return new Captura(logo, encode(snapshot));
    }

    /** Uso interno do processamento: não publica a logo nem persiste a URL temporária. */
    @Transactional(readOnly = true)
    public MarcaDaguaDTO prepararParaProcessamento(UUID profissionalId, MarcaDaguaSnapshotDTO snapshot) {
        var logo = logo(profissionalId, snapshot.logoImagemId(), false);
        String url = storage.obter(logo.getProvedor()).visualizar(logo.getChave(), Instant.now().plusSeconds(172800));
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException ex) { throw new ArmazenamentoException("Endereço da logo inválido para processamento."); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawFragment() != null
                || videoProperties.getWatermarkAllowedHosts().stream().noneMatch(host -> host.equalsIgnoreCase(uri.getHost()))) {
            throw new ArmazenamentoException("Configure um host HTTPS permitido para a logo de processamento.");
        }
        return new MarcaDaguaDTO(url, snapshot.posicao(), snapshot.larguraPercentual(), snapshot.opacidadePercentual(), snapshot.margemPercentual());
    }

    private ImagemAsset logo(UUID profissionalId, UUID id, boolean lock) {
        var imagem = (lock ? imagens.findOwnedForUpdate(id, profissionalId) : imagens.findByIdAndProfissional_Id(id, profissionalId))
                .orElseThrow(() -> new ResourceNotFoundException("Logo não encontrada ou acesso negado."));
        if (imagem.getStatus() != StatusImagem.PRONTA || imagem.getLargura() == null || imagem.getAltura() == null
                || imagem.getLargura() < 1 || imagem.getAltura() < 1 || imagem.getLargura() > MAX_LOGO_DIMENSAO
                || imagem.getAltura() > MAX_LOGO_DIMENSAO || imagem.getTamanhoBytes() > MAX_LOGO_BYTES
                || !(imagem.getTipo().equals("image/png") || imagem.getTipo().equals("image/jpeg"))) {
            throw new IllegalArgumentException("A logo precisa ser JPEG/PNG pronta, de até 2 MiB e 4096 pixels por dimensão.");
        }
        return imagem;
    }

    private void validar(MarcaDaguaPreferenciasUpdateDTO dto) {
        if (dto.ativaPadrao() == null || dto.posicao() == null || dto.larguraPercentual() == null
                || dto.larguraPercentual() < 1 || dto.larguraPercentual() > 100 || dto.opacidadePercentual() == null
                || dto.opacidadePercentual() < 1 || dto.opacidadePercentual() > 100 || dto.margemPercentual() == null
                || dto.margemPercentual() < 0 || dto.margemPercentual() > 40) {
            throw new IllegalArgumentException("Configuração de marca d'água inválida.");
        }
    }

    private MarcaDaguaPreferenciasDTO toDTO(PreferenciasMarcaDagua config) {
        var logo = config.getLogo();
        return new MarcaDaguaPreferenciasDTO(config.isAtivaPadrao(), logo == null ? null : logo.getId(),
                logo == null ? null : ImagemDTO.from(logo), config.getPosicao(), config.getLarguraPercentual(),
                config.getOpacidadePercentual(), config.getMargemPercentual(), config.getAtualizadoEm());
    }

    private String encode(MarcaDaguaSnapshotDTO snapshot) {
        try { return mapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Não foi possível capturar a marca d'água."); }
    }

    public record Captura(ImagemAsset logo, String json) {}
}
