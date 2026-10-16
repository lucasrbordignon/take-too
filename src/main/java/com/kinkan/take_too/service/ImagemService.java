package com.kinkan.take_too.service;

import com.kinkan.take_too.config.ImagemProperties;
import com.kinkan.take_too.domain.dto.*;
import com.kinkan.take_too.domain.entity.*;
import com.kinkan.take_too.domain.enums.*;
import com.kinkan.take_too.exception.*;
import com.kinkan.take_too.repository.*;
import com.kinkan.take_too.service.storage.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImagemService {
    private final ImagemAssetRepository imagens;
    private final ProfissionalRepository profissionais;
    private final ProjetoRepository projetos;
    private final VersaoRepository versoes;
    private final ImageStorageRegistry providers;
    private final ImagemProperties properties;

    private void habilitado() {
        if (!properties.isEnabled()) throw new ArmazenamentoException("Fluxo de imagens desativado.");
        if (properties.getMaxBytes() < 1 || properties.getMaxBytes() > 50L * 1024 * 1024 || properties.getMaxPixels() < 1) {
            throw new ArmazenamentoException("Limites de imagens não configurados corretamente.");
        }
    }
    private ImagemAsset owned(UUID profissionalId, UUID id, boolean lock) {
        habilitado();
        return (lock ? imagens.findOwnedForUpdate(id, profissionalId) : imagens.findByIdAndProfissional_Id(id, profissionalId))
                .orElseThrow(() -> new ResourceNotFoundException("Imagem não encontrada ou acesso negado."));
    }
    @Transactional
    public ImagemUploadDTO criarUpload(UUID profissionalId, @Nullable UUID projetoId, ImagemUploadCreateDTO dto) {
        habilitado();
        if (dto.nome() == null || dto.nome().isBlank() || dto.nome().length() > 255
                || dto.tipo() == null || !Set.of("image/jpeg", "image/png").contains(dto.tipo())
                || dto.tamanhoBytes() < 1 || dto.tamanhoBytes() > properties.getMaxBytes()) {
            throw new IllegalArgumentException("Informe nome, imagem JPEG/PNG e tamanho dentro do limite permitido.");
        }
        try {
            if (Base64.getDecoder().decode(dto.checksum()).length != 32) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Checksum SHA-256 inválido."); }
        Projeto projeto = null;
        if (projetoId != null) {
            projeto = projetos.findByIdAndProfissional_IdForUpdate(projetoId, profissionalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));
            if (projeto.getEtapaAtual() != EtapaProjeto.EM_EDICAO) throw new IllegalStateException("O projeto precisa estar em edição para enviar imagens.");
        }
        var imagem = new ImagemAsset();
        imagem.setId(UUID.randomUUID());
        imagem.setProfissional(profissionais.findById(profissionalId).orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado.")));
        imagem.setProjetoRascunho(projeto);
        imagem.setNome(dto.nome().trim()); imagem.setTipo(dto.tipo()); imagem.setChecksum(dto.checksum());
        imagem.setTamanhoBytes(dto.tamanhoBytes()); imagem.setProvedor(properties.getProvider());
        imagem.setChave("imagens/" + profissionalId + "/" + imagem.getId() + (dto.tipo().equals("image/png") ? ".png" : ".jpg"));
        imagem.setStatus(StatusImagem.AGUARDANDO_UPLOAD);
        imagens.save(imagem);
        return upload(imagem);
    }
    private ImagemUploadDTO upload(ImagemAsset imagem) {
        var expiry = Instant.now().plusSeconds(900);
        var session = providers.obter(imagem.getProvedor()).criarUpload(imagem.getChave(), imagem.getTipo(), imagem.getChecksum(), expiry);
        return new ImagemUploadDTO(ImagemDTO.from(imagem), session.url(), "PUT", session.headers(), expiry);
    }
    @Transactional(readOnly = true)
    public ImagemUploadDTO buscarUpload(UUID profissionalId, UUID id) {
        var imagem = owned(profissionalId, id, false);
        if (imagem.getStatus() != StatusImagem.AGUARDANDO_UPLOAD) throw new IllegalStateException("Esta imagem não aguarda upload.");
        return upload(imagem);
    }
    @Transactional(readOnly = true)
    public Page<ImagemDTO> listar(UUID profissionalId, @Nullable UUID projetoId, int pagina, int tamanho) {
        habilitado();
        if (pagina < 0 || tamanho < 1 || tamanho > 100) throw new IllegalArgumentException("Paginação inválida.");
        var pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        if (projetoId != null) {
            projetos.findByIdAndProfissional_Id(projetoId, profissionalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));
            return imagens.findByProjetoRascunho_IdAndProfissional_IdAndStatusNot(projetoId, profissionalId, StatusImagem.EXCLUIDA, pageable).map(ImagemDTO::from);
        }
        return imagens.findByProfissional_IdAndStatusNot(profissionalId, StatusImagem.EXCLUIDA, pageable).map(ImagemDTO::from);
    }
    @Transactional
    public ImagemDTO confirmar(UUID profissionalId, UUID id) {
        var imagem = owned(profissionalId, id, true);
        if (imagem.getStatus() == StatusImagem.PRONTA) return ImagemDTO.from(imagem);
        if (imagem.getStatus() != StatusImagem.AGUARDANDO_UPLOAD) throw new IllegalStateException("Esta imagem não pode ser confirmada.");
        var provider = providers.obter(imagem.getProvedor());
        var object = provider.consultar(imagem.getChave()).orElseThrow(() -> new IllegalStateException("O arquivo ainda não foi enviado."));
        try {
            if (object.tamanhoBytes() != imagem.getTamanhoBytes() || object.tamanhoBytes() > properties.getMaxBytes()
                    || !imagem.getTipo().equals(object.tipo())) throw new IllegalArgumentException("Arquivo diferente do upload autorizado.");
            byte[] bytes;
            try (var input = provider.abrir(imagem.getChave())) {
                bytes = input.readNBytes((int) properties.getMaxBytes() + 1);
            } catch (IOException ex) { throw new ArmazenamentoException("Não foi possível ler a imagem para validação."); }
            if (bytes.length != imagem.getTamanhoBytes() || !MessageDigest.isEqual(hash(bytes), Base64.getDecoder().decode(imagem.getChecksum()))) {
                throw new IllegalArgumentException("Tamanho ou checksum da imagem não confere.");
            }
            var dimensions = validarArquivo(bytes, imagem.getTipo());
            imagem.setLargura(dimensions[0]); imagem.setAltura(dimensions[1]); imagem.setStatus(StatusImagem.PRONTA);
        } catch (IllegalArgumentException ex) {
            imagem.setStatus(StatusImagem.ERRO); imagem.setErro(ex.getMessage());
        }
        imagem.setAtualizadoEm(Instant.now());
        return ImagemDTO.from(imagens.save(imagem));
    }
    private byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 indisponível."); }
    }
    private int[] validarArquivo(byte[] bytes, String tipo) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("O arquivo não é uma imagem JPEG/PNG válida.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (!(tipo.equals("image/png") && format.equalsIgnoreCase("png"))
                        && !(tipo.equals("image/jpeg") && format.equalsIgnoreCase("JPEG"))) {
                    throw new IllegalArgumentException("O conteúdo da imagem não corresponde ao tipo informado.");
                }
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > properties.getMaxPixels()) throw new IllegalArgumentException("A imagem excede o limite de resolução.");
                if (reader.read(0) == null) throw new IllegalArgumentException("Não foi possível decodificar a imagem.");
                return new int[]{width, height};
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new IllegalArgumentException("O arquivo de imagem está inválido ou incompleto."); }
    }
    @Transactional(readOnly = true)
    public ImagemVisualizacaoDTO visualizar(UUID profissionalId, UUID id) {
        var imagem = owned(profissionalId, id, false);
        if (imagem.getStatus() != StatusImagem.PRONTA) throw new IllegalStateException("A imagem não está pronta para visualização.");
        var expiry = Instant.now().plusSeconds(600);
        return new ImagemVisualizacaoDTO(ImagemDTO.from(imagem), providers.obter(imagem.getProvedor()).visualizar(imagem.getChave(), expiry), expiry);
    }
    @Transactional
    public ImagemAsset obterParaPublicacao(UUID profissionalId, UUID id) {
        var imagem = owned(profissionalId, id, true);
        if (imagem.getStatus() != StatusImagem.PRONTA) throw new IllegalStateException("A imagem não está pronta para publicação.");
        return imagem;
    }
    @Transactional
    public void excluir(UUID profissionalId, UUID id) {
        var imagem = owned(profissionalId, id, true);
        if (imagem.getStatus() == StatusImagem.EXCLUIDA) return;
        if (versoes.existsByImagem_Id(id)) throw new IllegalStateException("A imagem está vinculada a uma versão publicada e não pode ser excluída.");
        providers.obter(imagem.getProvedor()).excluir(imagem.getChave());
        imagem.setProjetoRascunho(null); imagem.setStatus(StatusImagem.EXCLUIDA); imagem.setAtualizadoEm(Instant.now());
        imagens.save(imagem);
    }
}
