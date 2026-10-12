package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.VersaoCreateDTO;
import com.kinkan.take_too.domain.dto.VersaoDTO;
import com.kinkan.take_too.domain.dto.VideoPlaybackDTO;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.entity.Versao;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusVersao;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ProjetoRepository;
import com.kinkan.take_too.repository.VersaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VersaoService {

    private final VersaoRepository versaoRepository;
    private final ProjetoRepository projetoRepository;
    private final VideoService videoService;

    @Transactional(readOnly = true)
    public List<VersaoDTO> listarVersoesDoProjeto(@NonNull UUID profissionalId, @NonNull UUID projetoId) {
        // Valida se projeto pertence ao profissional
        projetoRepository.findByIdAndProfissional_Id(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));

        return versaoRepository.findByProjeto_IdOrderByNumeroAsc(projetoId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public VersaoDTO criarVersao(@NonNull UUID profissionalId, @NonNull UUID projetoId, @NonNull VersaoCreateDTO dto) {
        if (!dto.isOrigemValida()) {
            throw new IllegalArgumentException("Informe exatamente uma origem: arquivoUrl válida ou videoId.");
        }
        // Lock pessimista para garantir que criações concorrentes no mesmo projeto sejam serializadas
        Projeto projeto = projetoRepository.findByIdAndProfissional_IdForUpdate(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado ou acesso negado."));

        // Validação de etapa do projeto: criação permitida exclusivamente na etapa EM_EDICAO
        if (projeto.getEtapaAtual() != EtapaProjeto.EM_EDICAO) {
            throw new IllegalArgumentException(String.format(
                    "Versões só podem ser criadas quando o projeto estiver na etapa EM_EDICAO. Etapa atual: %s.",
                    projeto.getEtapaAtual()));
        }

        // Descobre o próximo número de versão sob o lock
        int proximoNumero = versaoRepository.findMaxNumeroByProjetoId(projetoId).orElse(0) + 1;

        Versao versao = new Versao();
        versao.setNumero(proximoNumero);
        versao.setArquivoUrl(dto.arquivoUrl());
        if (dto.videoId() != null) {
            // Mesmo lock usado pela exclusão: não se publica um asset excluído em paralelo.
            var video = videoService.obterParaPublicacao(profissionalId, dto.videoId());
            versao.setVideo(video);
            if (video.getProjetoRascunho() != null && projetoId.equals(video.getProjetoRascunho().getId())) {
                video.setProjetoRascunho(null);
            }
        }
        versao.setProjeto(projeto);
        versao.setStatus(StatusVersao.DISPONIVEL);

        Versao salva = versaoRepository.save(versao);

        // Criação da versão move automaticamente o projeto para revisão do cliente
        projeto.setEtapaAtual(EtapaProjeto.REVISAO_CLIENTE);
        projetoRepository.save(projeto);

        return toDTO(salva);
    }

    private VersaoDTO toDTO(Versao versao) {
        return VersaoDTO.from(versao);
    }

    @Transactional(readOnly = true)
    public VideoPlaybackDTO criarPlayback(UUID profissionalId, UUID projetoId, UUID versaoId) {
        Versao versao = versaoRepository.findByIdAndProjeto_Profissional_Id(versaoId, profissionalId)
                .filter(v -> projetoId.equals(v.getProjeto().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Versão não encontrada ou acesso negado."));
        if (versao.getVideo() == null) {
            throw new IllegalStateException("Esta versão usa arquivoUrl; reproduza pela URL legada.");
        }
        return videoService.criarPlayback(profissionalId, versao.getVideo().getId());
    }
}
