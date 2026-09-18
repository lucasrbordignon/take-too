package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.Versao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VersaoRepository extends JpaRepository<Versao, UUID> {
    List<Versao> findByProjeto_IdOrderByNumeroAsc(UUID projetoId);

    Optional<Versao> findByIdAndProjeto_Profissional_Id(UUID id, UUID profissionalId);

    Optional<Versao> findTopByProjeto_IdOrderByNumeroDesc(UUID projetoId);

    @Query("SELECT MAX(v.numero) FROM Versao v WHERE v.projeto.id = :projetoId")
    Optional<Integer> findMaxNumeroByProjetoId(UUID projetoId);
}
