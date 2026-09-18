package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.domain.dto.ComentarioCreateDTO;
import com.kinkan.take_too.domain.dto.ComentarioDTO;
import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.dto.VersaoDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Comentario;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.entity.Versao;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusVersao;
import com.kinkan.take_too.exception.ForbiddenException;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ComentarioRepository;
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
public class PortalClienteService {

    private final ProjetoRepository projetoRepository;
    private final VersaoRepository versaoRepository;
    private final ComentarioRepository comentarioRepository;
    private final ClienteRepository clienteRepository;

    private void validarAcesso(UUID projetoIdAcessoToken, UUID projetoIdRequisicao) {
        if (!projetoIdAcessoToken.equals(projetoIdRequisicao)) {
            throw new ForbiddenException("Acesso negado a este projeto.");
        }
    }

    @Transactional(readOnly = true)
    public ProjetoDTO buscarProjeto(@NonNull UUID projetoIdAcesso, @NonNull UUID projetoId) {
        validarAcesso(projetoIdAcesso, projetoId);

        Projeto projeto = projetoRepository.findById(projetoId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado."));

        ClienteDTO clienteDTO = new ClienteDTO(
                projeto.getCliente().getId(),
                projeto.getCliente().getNome(),
                projeto.getCliente().getTelefone(),
                projeto.getCliente().getEmail()
        );
        return new ProjetoDTO(projeto.getId(), projeto.getNome(), projeto.getEtapaAtual(), clienteDTO);
    }

    @Transactional(readOnly = true)
    public List<VersaoDTO> listarVersoes(@NonNull UUID projetoIdAcesso, @NonNull UUID projetoId) {
        validarAcesso(projetoIdAcesso, projetoId);
        
        return versaoRepository.findByProjeto_IdOrderByNumeroAsc(projetoId)
                .stream()
                .map(v -> new VersaoDTO(v.getId(), v.getNumero(), v.getArquivoUrl(), v.getStatus().name()))
                .toList();
    }

    @Transactional
    public ComentarioDTO criarComentario(@NonNull UUID clienteId, @NonNull UUID projetoIdAcesso, @NonNull UUID versaoId, @NonNull ComentarioCreateDTO dto) {
        Versao versao = versaoRepository.findById(versaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Versão não encontrada."));

        validarAcesso(projetoIdAcesso, versao.getProjeto().getId());

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));

        Comentario comentario = new Comentario();
        comentario.setTexto(dto.texto());
        comentario.setTimestampSegundos(dto.tempoVideo() != null ? dto.tempoVideo() : 0);
        comentario.setClienteAutor(cliente);
        comentario.setVersao(versao);

        Comentario salvo = comentarioRepository.save(comentario);

        return new ComentarioDTO(
                salvo.getId(),
                salvo.getTexto(),
                salvo.getTimestampSegundos(),
                "CLIENTE"
        );
    }

    @Transactional
    public void alterarStatusVersao(@NonNull UUID projetoIdAcesso, @NonNull UUID versaoId, @NonNull StatusVersao novoStatus) {
        if (novoStatus != StatusVersao.APROVADA && novoStatus != StatusVersao.REJEITADA) {
            throw new IllegalArgumentException("Status inválido para avaliação da versão. Use apenas APROVADA ou REJEITADA.");
        }

        Versao versao = versaoRepository.findById(versaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Versão não encontrada."));

        Projeto projeto = versao.getProjeto();
        validarAcesso(projetoIdAcesso, projeto.getId());

        // Valida se o projeto está em etapa de revisão
        if (projeto.getEtapaAtual() != EtapaProjeto.REVISAO_CLIENTE) {
            throw new IllegalStateException("O projeto não está em fase de revisão pelo cliente.");
        }

        // Valida se é a versão mais recente do projeto
        var ultimaVersaoOpt = versaoRepository.findTopByProjeto_IdOrderByNumeroDesc(projeto.getId());
        if (ultimaVersaoOpt.isEmpty() || !ultimaVersaoOpt.get().getId().equals(versaoId)) {
            throw new IllegalArgumentException("Apenas a versão mais recente do projeto pode ser avaliada.");
        }

        // Valida se a versão atual ainda está disponível para avaliação
        if (versao.getStatus() != StatusVersao.DISPONIVEL) {
            throw new IllegalStateException("Esta versão já foi previamente avaliada como " + versao.getStatus() + ".");
        }

        versao.setStatus(novoStatus);
        versaoRepository.save(versao);

        // Atualiza etapa do projeto automaticamente
        if (novoStatus == StatusVersao.APROVADA) {
            projeto.setEtapaAtual(EtapaProjeto.APROVADO);
            projetoRepository.save(projeto);
        } else {
            // Rejeição mantém a versão histórica como REJEITADA e volta o projeto para EM_EDICAO
            projeto.setEtapaAtual(EtapaProjeto.EM_EDICAO);
            projetoRepository.save(projeto);
        }
    }
}
