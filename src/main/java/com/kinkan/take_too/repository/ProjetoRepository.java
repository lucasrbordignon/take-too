package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.Projeto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjetoRepository extends JpaRepository<Projeto, UUID> {

    // Busca todos os projetos de um profissional
    List<Projeto> findByProfissional_Id(UUID profissionalId);

    // Busca um projeto específico garantindo que seja do profissional
    Optional<Projeto> findByIdAndProfissional_Id(UUID id, UUID profissionalId);

    // Lock pessimista para garantir que criações de versões concorrentes sejam serializadas
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Projeto p WHERE p.id = :id AND p.profissional.id = :profissionalId")
    Optional<Projeto> findByIdAndProfissional_IdForUpdate(@Param("id") UUID id, @Param("profissionalId") UUID profissionalId);
}
