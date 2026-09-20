package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ClienteCreateDTO;
import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.domain.entity.Projeto;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ProjetoRepository projetoRepository;

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarClientes(@NonNull UUID profissionalId) {
        List<Cliente> clientes = clienteRepository.findByProfissionais_Id(profissionalId);
        List<Projeto> projetos = projetoRepository.findByProfissional_Id(profissionalId);

        Map<UUID, List<Projeto>> projetosPorCliente = projetos.stream()
                .filter(p -> p.getCliente() != null && p.getCliente().getId() != null)
                .collect(Collectors.groupingBy(p -> p.getCliente().getId()));

        return clientes.stream().map(c -> {
            List<Projeto> projs = projetosPorCliente.getOrDefault(c.getId(), List.of());
            int totalProjetos = projs.size();
            Instant ultimaAtividade = projs.stream()
                    .map(p -> p.getAtualizadoEm() != null ? p.getAtualizadoEm() : p.getCriadoEm())
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(c.getCriadoEm());

            return new ClienteDTO(
                    c.getId(),
                    c.getNome(),
                    c.getTelefone(),
                    c.getEmail(),
                    c.getCriadoEm(),
                    totalProjetos,
                    ultimaAtividade
            );
        }).toList();
    }

    @Transactional
    public ClienteDTO criarCliente(@NonNull UUID profissionalId, ClienteCreateDTO dto) {
        Profissional profissional = profissionalRepository.findById(profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado"));

        String telefoneNormalizado = com.kinkan.take_too.util.PhoneUtils.normalize(dto.telefone());
        Cliente cliente = clienteRepository.findByTelefone(telefoneNormalizado).orElse(null);

        if (cliente == null) {
            cliente = new Cliente();
            cliente.setNome(dto.nome());
            cliente.setTelefone(telefoneNormalizado);
            cliente.setEmail(dto.email());
        } else {
            if (cliente.getProfissionais().contains(profissional)) {
                throw new IllegalArgumentException("Cliente com este telefone já está cadastrado no seu portfólio.");
            }
        }

        // Vincula o cliente ao profissional
        cliente.getProfissionais().add(profissional);
        profissional.getClientes().add(cliente);

        Cliente salvo = clienteRepository.save(cliente);
        return new ClienteDTO(
                salvo.getId(),
                salvo.getNome(),
                salvo.getTelefone(),
                salvo.getEmail(),
                salvo.getCriadoEm(),
                0,
                salvo.getCriadoEm()
        );
    }

    @Transactional(readOnly = true)
    public ClienteDTO buscarPorId(@NonNull UUID profissionalId, @NonNull UUID id) {
        Cliente cliente = clienteRepository.findByIdAndProfissionais_Id(id, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado ou não pertence a este profissional"));

        List<Projeto> projs = projetoRepository.findByProfissional_Id(profissionalId).stream()
                .filter(p -> p.getCliente() != null && id.equals(p.getCliente().getId()))
                .toList();

        int totalProjetos = projs.size();
        Instant ultimaAtividade = projs.stream()
                .map(p -> p.getAtualizadoEm() != null ? p.getAtualizadoEm() : p.getCriadoEm())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(cliente.getCriadoEm());

        return new ClienteDTO(
                cliente.getId(),
                cliente.getNome(),
                cliente.getTelefone(),
                cliente.getEmail(),
                cliente.getCriadoEm(),
                totalProjetos,
                ultimaAtividade
        );
    }
}
