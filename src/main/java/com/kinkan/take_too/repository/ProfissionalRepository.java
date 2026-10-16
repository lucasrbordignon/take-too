package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Profissional p WHERE p.id = :id")
    Optional<Profissional> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<Profissional> findByEmail(String email);
}
