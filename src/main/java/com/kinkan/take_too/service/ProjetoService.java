package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.AtividadeDTO;
import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.domain.dto.ProjetoCreateDTO;
import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Comentario;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.entity.Versao;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.domain.enums.StatusVersao;
import com.kinkan.take_too.exception.ForbiddenException;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ComentarioRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import com.kinkan.take_too.repository.VersaoRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjetoService {

    private final ProjetoRepository projetoRepository;
    private final ClienteRepository clienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final VersaoRepository versaoRepository;
    private final ComentarioRepository comentarioRepository;

    @Transactional(readOnly = true)
    public List<ProjetoDTO> listarProjetos(@NonNull UUID profissionalId) {
        return projetoRepository.findByProfissional_Id(profissionalId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public ProjetoDTO criarProjeto(@NonNull UUID profissionalId,
            @NonNull ProjetoCreateDTO dto) {
        Profissional profissional = profissionalRepository.findById(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado"));

        // Garante que o cliente pertence a este profissional
        Cliente cliente = clienteRepository.findByIdAndProfissionais_Id(dto.clienteId(), profissionalId)
                .orElseThrow(() -> new ForbiddenException("Cliente não pertence ao profissional ou não existe"));

        Projeto projeto = new Projeto();
        projeto.setNome(dto.nome());
        projeto.setProfissional(profissional);
        projeto.setCliente(cliente);
        projeto.setEtapaAtual(EtapaProjeto.GRAVACAO_CONCLUIDA);

        Projeto salvo = projetoRepository.save(projeto);
        return toDTO(salvo);
    }

    @Transactional(readOnly = true)
    public ProjetoDTO buscarPorId(@NonNull UUID profissionalId,
            @NonNull UUID projetoId) {
        Projeto projeto = projetoRepository.findByIdAndProfissional_Id(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Projeto não encontrado ou não pertence a este profissional"));
        return toDTO(projeto);
    }

    private ProjetoDTO toDTO(Projeto projeto) {
        ClienteDTO clienteDTO = new ClienteDTO(
                projeto.getCliente().getId(),
                projeto.getCliente().getNome(),
                projeto.getCliente().getTelefone(),
                projeto.getCliente().getEmail(),
                projeto.getCliente().getCriadoEm());
        return new ProjetoDTO(
                projeto.getId(),
                projeto.getNome(),
                projeto.getEtapaAtual(),
                clienteDTO,
                projeto.getCriadoEm(),
                projeto.getAtualizadoEm(),
                projeto.isMagicLinkAtivo());
    }

    @Transactional
    public ProjetoDTO atualizarStatus(@NonNull UUID profissionalId, @NonNull UUID projetoId,
            @NonNull EtapaProjeto novoStatus) {
        Projeto projeto = projetoRepository.findByIdAndProfissional_Id(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Projeto não encontrado ou não pertence a este profissional"));

        // Regras da Máquina de Estados
        if (!projeto.getEtapaAtual().podeTransitarPara(novoStatus)) {
            throw new IllegalArgumentException(
                    String.format("Transição de etapa inválida: não é permitido transitar de %s para %s.",
                            projeto.getEtapaAtual(), novoStatus));
        }

        // A transição para REVISAO_CLIENTE ocorre exclusivamente através da criação de uma nova versão
        if (novoStatus == EtapaProjeto.REVISAO_CLIENTE) {
            throw new IllegalArgumentException(
                    "A transição para REVISAO_CLIENTE ocorre exclusivamente através da criação de uma nova versão.");
        }

        projeto.setEtapaAtual(novoStatus);
        projetoRepository.save(projeto);

        return toDTO(projeto);
    }

    @Transactional
    public void revogarMagicLink(@NonNull UUID profissionalId,
            @NonNull UUID projetoId) {
        Projeto projeto = projetoRepository.findByIdAndProfissional_Id(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Projeto não encontrado ou não pertence a este profissional"));

        projeto.setMagicLinkAtivo(false);
        projetoRepository.save(projeto);
    }

    @Transactional(readOnly = true)
    public List<AtividadeDTO> listarAtividades(@NonNull UUID profissionalId, @NonNull UUID projetoId) {
        Projeto projeto = projetoRepository.findByIdAndProfissional_Id(projetoId, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Projeto não encontrado ou não pertence a este profissional"));

        List<AtividadeDTO> atividades = new ArrayList<>();

        // 1. Evento de criação do projeto
        atividades.add(new AtividadeDTO(
                "proj-" + projeto.getId(),
                "PROJETO_CRIADO",
                "Projeto criado",
                "Projeto iniciado com o cliente " + projeto.getCliente().getNome(),
                projeto.getProfissional().getNome(),
                "PROFISSIONAL",
                projeto.getCriadoEm(),
                null,
                null,
                null
        ));

        // 2. Versões e comentários vinculados
        List<Versao> versoes = versaoRepository.findByProjeto_IdOrderByNumeroAsc(projetoId);
        for (Versao versao : versoes) {
            atividades.add(new AtividadeDTO(
                    "ver-" + versao.getId(),
                    "VERSAO_PUBLICADA",
                    "Versão " + versao.getNumero() + " publicada",
                    "Nova versão disponível para avaliação",
                    projeto.getProfissional().getNome(),
                    "PROFISSIONAL",
                    versao.getCriadoEm(),
                    versao.getId(),
                    versao.getNumero(),
                    null
            ));

            if (versao.getStatus() == StatusVersao.APROVADA) {
                atividades.add(new AtividadeDTO(
                        "status-" + versao.getId(),
                        "VERSAO_APROVADA",
                        "Versão " + versao.getNumero() + " aprovada",
                        "O cliente aprovou esta versão do projeto",
                        projeto.getCliente().getNome(),
                        "CLIENTE",
                        versao.getCriadoEm().plusSeconds(1),
                        versao.getId(),
                        versao.getNumero(),
                        null
                ));
            } else if (versao.getStatus() == StatusVersao.REJEITADA) {
                atividades.add(new AtividadeDTO(
                        "status-" + versao.getId(),
                        "VERSAO_REJEITADA",
                        "Versão " + versao.getNumero() + " precisa de ajustes",
                        "O cliente solicitou alterações nesta versão",
                        projeto.getCliente().getNome(),
                        "CLIENTE",
                        versao.getCriadoEm().plusSeconds(1),
                        versao.getId(),
                        versao.getNumero(),
                        null
                ));
            }

            List<Comentario> comentarios = comentarioRepository.findByVersao_IdOrderByTimestampSegundosAsc(versao.getId());
            for (Comentario c : comentarios) {
                boolean isCliente = c.getClienteAutor() != null;
                String autorNome = isCliente ? c.getClienteAutor().getNome() : projeto.getProfissional().getNome();
                String autorTipo = isCliente ? "CLIENTE" : "PROFISSIONAL";
                String timecodeStr = formatTimecode(c.getTimestampSegundos());

                atividades.add(new AtividadeDTO(
                        "com-" + c.getId(),
                        "COMENTARIO_ADICIONADO",
                        (isCliente ? "Cliente comentou" : "Comentário do editor") + " aos " + timecodeStr,
                        c.getTexto(),
                        autorNome,
                        autorTipo,
                        c.getCriadoEm(),
                        versao.getId(),
                        versao.getNumero(),
                        c.getTimestampSegundos()
                ));
            }
        }

        // Ordena cronologicamente: do mais recente para o mais antigo
        atividades.sort(Comparator.comparing(AtividadeDTO::timestamp).reversed());
        return atividades;
    }

    private String formatTimecode(Integer totalSeconds) {
        if (totalSeconds == null || totalSeconds <= 0) {
            return "00:00";
        }
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }
}
