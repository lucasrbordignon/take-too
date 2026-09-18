package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    
    // Busca todos os clientes vinculados a um profissional (Isolamento)
    List<Cliente> findByProfissionais_Id(UUID profissionalId);

    // Busca um cliente específico apenas se estiver vinculado ao profissional
    Optional<Cliente> findByIdAndProfissionais_Id(UUID id, UUID profissionalId);
    Optional<Cliente> findByTelefone(String telefone);
}
