package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.domain.dto.ProjetoCreateDTO;
import com.kinkan.take_too.domain.dto.ProjetoDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.domain.enums.EtapaProjeto;
import com.kinkan.take_too.exception.ForbiddenException;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjetoService {

    private final ProjetoRepository projetoRepository;
    private final ClienteRepository clienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final com.kinkan.take_too.repository.VersaoRepository versaoRepository;

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
}
