package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.Comentario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComentarioRepository extends JpaRepository<Comentario, UUID> {
    List<Comentario> findByVersao_IdOrderByTimestampSegundosAsc(UUID versaoId);
}
