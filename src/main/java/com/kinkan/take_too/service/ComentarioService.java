package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ComentarioCreateDTO;
import com.kinkan.take_too.domain.dto.ComentarioDTO;
import com.kinkan.take_too.domain.entity.Comentario;
import com.kinkan.take_too.domain.entity.Versao;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ComentarioRepository;
import com.kinkan.take_too.repository.VersaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComentarioService {

    private final ComentarioRepository comentarioRepository;
    private final VersaoRepository versaoRepository;

    @Transactional(readOnly = true)
    public List<ComentarioDTO> listarComentariosDaVersao(UUID profissionalId, UUID versaoId) {
        // Valida se a versão pertence ao profissional (indiretamente valida o projeto)
        versaoRepository.findByIdAndProjeto_Profissional_Id(versaoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Versão não encontrada ou acesso negado."));

        return comentarioRepository.findByVersao_IdOrderByTimestampSegundosAsc(versaoId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public ComentarioDTO criarComentario(UUID profissionalId, UUID versaoId, ComentarioCreateDTO dto) {
        Versao versao = versaoRepository.findByIdAndProjeto_Profissional_Id(versaoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Versão não encontrada ou acesso negado."));

        Comentario comentario = new Comentario();
        comentario.setTexto(dto.texto());
        comentario.setTimestampSegundos(dto.tempoVideo() != null ? dto.tempoVideo() : 0);
        // Não vinculamos o cliente_id aqui, ou seja, é um comentário do profissional
        comentario.setVersao(versao);

        Comentario salvo = comentarioRepository.save(comentario);
        return toDTO(salvo);
    }

    private ComentarioDTO toDTO(Comentario comentario) {
        return new ComentarioDTO(
                comentario.getId(),
                comentario.getTexto(),
                comentario.getTimestampSegundos(),
                comentario.getClienteAutor() == null ? "PROFISSIONAL" : "CLIENTE",
                comentario.getCriadoEm());
    }
}
