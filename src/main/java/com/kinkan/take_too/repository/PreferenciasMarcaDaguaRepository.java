package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.PreferenciasMarcaDagua;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PreferenciasMarcaDaguaRepository extends JpaRepository<PreferenciasMarcaDagua, UUID> {
    boolean existsByLogo_Id(UUID imagemId);
}
