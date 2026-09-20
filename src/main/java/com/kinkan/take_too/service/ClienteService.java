package com.kinkan.take_too.service;

import com.kinkan.take_too.domain.dto.ClienteCreateDTO;
import com.kinkan.take_too.domain.dto.ClienteDTO;
import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import com.kinkan.take_too.exception.ResourceNotFoundException;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ProfissionalRepository profissionalRepository;

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarClientes(@NonNull UUID profissionalId) {
        return clienteRepository.findByProfissionais_Id(profissionalId)
                .stream()
                .map(this::toDTO)
                .toList();
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
        return toDTO(salvo);
    }

    @Transactional(readOnly = true)
    public ClienteDTO buscarPorId(@NonNull UUID profissionalId, @NonNull UUID id) {
        Cliente cliente = clienteRepository.findByIdAndProfissionais_Id(id, profissionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado ou não pertence a este profissional"));
        return toDTO(cliente);
    }

    private ClienteDTO toDTO(Cliente cliente) {
        return new ClienteDTO(cliente.getId(), cliente.getNome(), cliente.getTelefone(), cliente.getEmail(), cliente.getCriadoEm());
    }
}
