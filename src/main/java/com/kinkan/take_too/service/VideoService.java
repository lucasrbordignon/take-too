package com.kinkan.take_too.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinkan.take_too.config.VideoProperties;
import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.domain.entity.VideoAsset;
import com.kinkan.take_too.domain.enums.StatusVideo;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.exception.VideoProviderException;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.VideoAssetRepository;
import com.kinkan.take_too.repository.VersaoRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.service.video.VideoProvider;
import com.kinkan.take_too.service.video.VideoProviderRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class VideoService {
    private final VideoAssetRepository repository;
    private final ProfissionalRepository profissionais;
    private final VideoProviderRegistry providers;
    private final VideoProperties properties;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final VersaoRepository versoes;
    private final ProjetoRepository projetos;
    private final MarcaDaguaService marcaDagua;

    public VideoService(VideoAssetRepository repository, ProfissionalRepository profissionais,
                        VideoProviderRegistry providers, VideoProperties properties, ObjectMapper mapper,
                        PlatformTransactionManager transactionManager, VersaoRepository versoes, ProjetoRepository projetos, MarcaDaguaService marcaDagua) {
        this.repository = repository;
        this.profissionais = profissionais;
        this.providers = providers;
        this.properties = properties;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.versoes = versoes;
        this.projetos = projetos;
        this.marcaDagua = marcaDagua;
    }

    public VideoUploadDTO criarUpload(UUID profissionalId, VideoUploadCreateDTO dto) {
        return criarUpload(profissionalId, dto, null);
    }

    public VideoUploadDTO criarUploadNoProjeto(UUID profissionalId, UUID projetoId, VideoUploadCreateDTO dto) {
        return criarUpload(profissionalId, dto, projetoId);
    }

    private VideoUploadDTO criarUpload(UUID profissionalId, VideoUploadCreateDTO dto, @Nullable UUID projetoId) {
        validarHabilitado();
        validarOrigem(dto.origem());
        validarMarcaDagua(dto.marcaDagua());
        VideoProvider provider = providers.obter(properties.getProvider());
        String watermark = encode(dto.marcaDagua());
        // A correlação fica disponível antes da chamada externa, inclusive para webhooks precoces.
        UUID id = Objects.requireNonNull(transactions.execute(tx -> {
            Projeto projeto = null;
            if (projetoId != null) {
                projeto = projetos.findByIdAndProfissional_IdForUpdate(projetoId, profissionalId)
                        .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));
                if (projeto.getEtapaAtual() != EtapaProjeto.EM_EDICAO) {
                    throw new IllegalStateException("Uploads no projeto exigem a etapa EM_EDICAO.");
                }
            }
            VideoAsset video = new VideoAsset();
            video.setProjetoRascunho(projeto);
            video.setId(UUID.randomUUID());
            video.setProfissional(profissionais.findById(profissionalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado.")));
            video.setNome(dto.nome().strip());
            video.setProvedor(provider.id());
            video.setStatus(StatusVideo.CRIANDO_UPLOAD);
            video.setMarcaDaguaJson(watermark);
            // A marca legada explícita tem precedência. Preferências são planejadas para a etapa de processamento.
            var captura = dto.marcaDagua() == null ? marcaDagua.capturar(profissionalId) : null;
            if (captura != null) { video.setLogoMarcaDagua(captura.logo()); video.setMarcaDaguaPreferenciasJson(captura.json()); }
            repository.saveAndFlush(video);
            return video.getId();
        }));
        VideoProvider.Upload upload;
        try {
            upload = provider.criarUpload(id, dto.origem(), dto.marcaDagua());
        } catch (RuntimeException ex) {
            transactions.executeWithoutResult(tx -> {
                VideoAsset video = locked(id);
                video.setStatus(StatusVideo.ERRO);
                video.setErro("Não foi possível criar o upload. Inicie um novo upload.");
                video.setAtualizadoEm(Instant.now());
            });
            throw ex;
        }
        VideoDTO video = Objects.requireNonNull(transactions.execute(tx -> {
            VideoAsset asset = locked(id);
            asset.setUploadExternoId(upload.id());
            asset.setUploadExpiraEm(upload.expiraEm());
            asset.setStatus(StatusVideo.AGUARDANDO_UPLOAD);
            asset.setAtualizadoEm(Instant.now());
            return toDTO(asset);
        }));
        return new VideoUploadDTO(video, upload.url(), upload.metodo(), upload.protocolo(), upload.headers(), upload.expiraEm());
    }

    @Transactional(readOnly = true)
    public Page<VideoDTO> listar(UUID profissionalId, int pagina, int tamanho) {
        validarHabilitado();
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new IllegalArgumentException("Página deve ser não negativa e tamanho deve estar entre 1 e 100.");
        }
        return repository.findByProfissional_Id(profissionalId,
                PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm", "id"))).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public VideoDTO buscar(UUID profissionalId, UUID videoId) {
        validarHabilitado();
        return toDTO(owned(profissionalId, videoId));
    }

    @Transactional
    public VideoDTO sincronizar(UUID profissionalId, UUID videoId) {
        validarHabilitado();
        VideoAsset video = lockedOwned(profissionalId, videoId);
        atualizar(video);
        return toDTO(video);
    }

    @Transactional(readOnly = true)
    public VideoUploadDTO buscarUpload(UUID profissionalId, UUID videoId) {
        validarHabilitado();
        VideoAsset video = owned(profissionalId, videoId);
        if (video.getStatus() != StatusVideo.AGUARDANDO_UPLOAD || video.getUploadExpiraEm() == null
                || !video.getUploadExpiraEm().isAfter(Instant.now())) {
            throw new IllegalStateException("Este vídeo não possui um upload ativo.");
        }
        VideoProvider.Upload upload = providers.obter(video.getProvedor()).consultarUpload(uploadId(video));
        return new VideoUploadDTO(toDTO(video), upload.url(), upload.metodo(), upload.protocolo(),
                upload.headers(), video.getUploadExpiraEm());
    }

    @Transactional(readOnly = true)
    public VideoPlaybackDTO criarPlayback(UUID profissionalId, UUID videoId) {
        validarHabilitado();
        VideoAsset video = owned(profissionalId, videoId);
        if (video.getStatus() != StatusVideo.PRONTO || video.getPlaybackExternoId() == null) {
            throw new IllegalStateException("O vídeo ainda não está pronto para reprodução.");
        }
        // A URL deve cobrir a duração inteira; o player pode solicitar uma nova sessão ao expirar.
        long duration = video.getDuracaoSegundos() == null ? 0 : (long) Math.ceil(video.getDuracaoSegundos());
        Instant expiry = Instant.now().plusSeconds(Math.max(properties.getPlaybackTtlSeconds(), duration + 300));
        VideoProvider.Playback playback = providers.obter(video.getProvedor())
                .criarPlayback(video.getPlaybackExternoId(), expiry);
        return new VideoPlaybackDTO(toDTO(video), playback.url(), playback.tipo(), playback.expiraEm(), playback.miniaturaUrl());
    }

    @Transactional
    public VideoAsset obterParaPublicacao(UUID profissionalId, UUID videoId) {
        validarHabilitado();
        VideoAsset video = lockedOwned(profissionalId, videoId);
        if (video.getStatus() != StatusVideo.PRONTO || video.getPlaybackExternoId() == null) {
            throw new IllegalStateException("Somente um vídeo pronto pode ser publicado.");
        }
        return video;
    }

    @Transactional(readOnly = true)
    public Page<VideoDTO> listarRascunhos(UUID profissionalId, UUID projetoId, int pagina, int tamanho) {
        validarHabilitado();
        projetos.findByIdAndProfissional_Id(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new IllegalArgumentException("Página deve ser não negativa e tamanho deve estar entre 1 e 100.");
        }
        return repository.findByProjetoRascunho_IdAndProfissional_IdAndStatusNot(projetoId, profissionalId,
                StatusVideo.EXCLUIDO, PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm", "id")))
                .map(this::toDTO);
    }

    @Transactional
    public void desvincularRascunho(UUID profissionalId, UUID projetoId, UUID videoId) {
        validarHabilitado();
        projetos.findByIdAndProfissional_IdForUpdate(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));
        VideoAsset video = lockedOwned(profissionalId, videoId);
        if (video.getProjetoRascunho() == null || !projetoId.equals(video.getProjetoRascunho().getId())) {
            throw new ResourceNotFoundException("Rascunho não encontrado neste projeto.");
        }
        video.setProjetoRascunho(null);
        video.setAtualizadoEm(Instant.now());
    }

    @Transactional
    public void excluir(UUID profissionalId, UUID videoId) {
        validarHabilitado();
        VideoAsset video = lockedOwned(profissionalId, videoId);
        if (video.getStatus() == StatusVideo.EXCLUIDO) { return; }
        if (versoes.existsByVideo_Id(videoId)) {
            throw new IllegalStateException("O vídeo está vinculado a uma versão publicada e não pode ser excluído.");
        }
        if (video.getStatus() == StatusVideo.CRIANDO_UPLOAD) {
            throw new IllegalStateException("Aguarde a criação do upload antes de excluir o vídeo.");
        }
        if (video.getUploadExternoId() != null) {
            providers.obter(video.getProvedor()).excluir(video.getId(), video.getUploadExternoId(), video.getAssetExternoId());
        }
        video.setStatus(StatusVideo.EXCLUIDO);
        video.setProjetoRascunho(null);
        video.setPlaybackExternoId(null);
        video.setAtualizadoEm(Instant.now());
    }

    @Transactional
    public void receberWebhook(String provedor, byte[] corpo, @Nullable String assinatura) {
        validarHabilitado();
        VideoProvider.Evento evento = providers.obter(provedor).verificarWebhook(corpo, assinatura);
        if (evento == null) { return; }
        VideoAsset associated = null;
        if (evento.uploadId() != null) {
            associated = repository.findByProvedorAndUploadExternoId(provedor, evento.uploadId()).orElse(null);
        }
        if (associated == null && evento.assetId() != null) {
            associated = repository.findByProvedorAndAssetExternoId(provedor, evento.assetId()).orElse(null);
        }
        if (associated == null && evento.videoId() != null) {
            associated = repository.findById(evento.videoId()).filter(v -> v.getProvedor().equals(provedor)).orElse(null);
        }
        // Outros assets da mesma conta não pertencem a este fluxo.
        if (associated == null) { return; }
        VideoAsset video = locked(associated.getId());
        if (video.getStatus() == StatusVideo.EXCLUIDO) { return; }
        // Referências externas vêm da criação/consulta, nunca do corpo do webhook.
        // 503 enquanto a criação termina faz o Mux repetir um webhook precoce.
        atualizar(video);
    }

    private void atualizar(VideoAsset video) {
        if (video.getStatus() == StatusVideo.EXCLUIDO) { return; }
        VideoProvider.Snapshot snapshot = providers.obter(video.getProvedor())
                .consultar(video.getId(), uploadId(video), video.getAssetExternoId());
        if (video.getStatus() == StatusVideo.PRONTO && (snapshot.status() == StatusVideo.PROCESSANDO
                || snapshot.status() == StatusVideo.AGUARDANDO_UPLOAD)) { return; }
        if (video.getAssetExternoId() != null && snapshot.assetId() != null
                && !video.getAssetExternoId().equals(snapshot.assetId())) {
            throw new VideoProviderException("O provedor retornou outro asset para este vídeo.");
        }
        String assetId = snapshot.assetId() != null ? snapshot.assetId() : video.getAssetExternoId();
        if (video.getStatus() == snapshot.status() && Objects.equals(video.getAssetExternoId(), assetId)
                && Objects.equals(video.getPlaybackExternoId(), snapshot.playbackId())
                && Objects.equals(video.getDuracaoSegundos(), snapshot.duracaoSegundos())
                && Objects.equals(video.getLargura(), snapshot.largura())
                && Objects.equals(video.getAltura(), snapshot.altura())
                && Objects.equals(video.getProporcao(), snapshot.proporcao())
                && Objects.equals(video.getErro(), snapshot.erro())) { return; }
        video.setStatus(snapshot.status());
        video.setAssetExternoId(assetId);
        video.setPlaybackExternoId(snapshot.playbackId());
        video.setDuracaoSegundos(snapshot.duracaoSegundos());
        video.setLargura(snapshot.largura());
        video.setAltura(snapshot.altura());
        video.setProporcao(snapshot.proporcao());
        video.setErro(snapshot.erro());
        video.setAtualizadoEm(Instant.now());
    }

    private String uploadId(VideoAsset video) {
        if (video.getUploadExternoId() == null) {
            throw new VideoProviderException("O upload ainda não possui referência externa; tente novamente mais tarde.");
        }
        return video.getUploadExternoId();
    }

    private VideoAsset owned(UUID profissionalId, UUID videoId) {
        return repository.findByIdAndProfissional_Id(videoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Vídeo não encontrado ou acesso negado."));
    }

    private VideoAsset locked(UUID videoId) {
        return repository.findByIdForUpdate(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Vídeo não encontrado."));
    }

    private VideoAsset lockedOwned(UUID profissionalId, UUID videoId) {
        VideoAsset video = locked(videoId);
        if (!video.getProfissional().getId().equals(profissionalId)) {
            throw new ResourceNotFoundException("Vídeo não encontrado ou acesso negado.");
        }
        return video;
    }

    private void validarHabilitado() {
        if (!properties.isEnabled()) { throw new VideoProviderException("Fluxo de vídeo independente desativado."); }
        if (properties.getPlaybackTtlSeconds() < 60 || properties.getPlaybackTtlSeconds() > 86400) {
            throw new VideoProviderException("Prazo de reprodução de vídeo inválido.");
        }
    }

    private void validarOrigem(String origem) {
        URI uri = uri(origem);
        boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || !properties.getAllowedOrigins().contains(origem)) {
            throw new IllegalArgumentException("Origem do upload não permitida.");
        }
    }

    private void validarMarcaDagua(@Nullable MarcaDaguaDTO watermark) {
        if (watermark == null) { return; }
        URI uri = uri(watermark.imagemUrl());
        String host = uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawFragment() != null
                || (!path.endsWith(".png") && !path.endsWith(".jpg") && !path.endsWith(".jpeg"))
                || properties.getWatermarkAllowedHosts().stream().noneMatch(h -> h.equalsIgnoreCase(host))) {
            throw new IllegalArgumentException("Marca d'água exige uma imagem PNG/JPG HTTPS em um host permitido.");
        }
    }

    private URI uri(String value) {
        try { return URI.create(value); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("URL de vídeo inválida."); }
    }

    private @Nullable String encode(@Nullable MarcaDaguaDTO watermark) {
        if (watermark == null) { return null; }
        try { return mapper.writeValueAsString(watermark); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Marca d'água inválida."); }
    }

    private VideoDTO toDTO(VideoAsset video) {
        MarcaDaguaDTO watermark = null;
        if (video.getMarcaDaguaJson() != null) {
            try { watermark = mapper.readValue(video.getMarcaDaguaJson(), MarcaDaguaDTO.class); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("Configuração de marca d'água inválida."); }
        }
        String ratio = video.getProporcao();
        String orientation = null;
        if (video.getLargura() != null && video.getAltura() != null) {
            double aspect = (double) video.getLargura() / video.getAltura();
            if (ratio != null && ratio.matches("[1-9][0-9]*:[1-9][0-9]*")) {
                String[] parts = ratio.split(":");
                aspect = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            } else { ratio = video.getLargura() + ":" + video.getAltura(); }
            orientation = aspect < 1 ? "VERTICAL" : aspect > 1 ? "PAISAGEM" : "QUADRADO";
        }
        return new VideoDTO(video.getId(), video.getNome(), video.getStatus(), video.getDuracaoSegundos(),
                video.getLargura(), video.getAltura(), ratio, orientation, watermark, video.getErro(),
                video.getCriadoEm(), video.getAtualizadoEm(), MarcaDaguaSnapshotDTO.fromJson(video.getMarcaDaguaPreferenciasJson()));
    }
}
