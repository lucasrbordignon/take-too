package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {
    Optional<Profissional> findByEmail(String email);
}
