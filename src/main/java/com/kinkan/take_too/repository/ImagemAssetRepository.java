package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.ImagemAsset;
import com.kinkan.take_too.domain.enums.StatusImagem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ImagemAssetRepository extends JpaRepository<ImagemAsset, UUID> {
    Page<ImagemAsset> findByProfissional_IdAndStatusNot(UUID profissionalId, StatusImagem status, Pageable pageable);
    Page<ImagemAsset> findByProjetoRascunho_IdAndProfissional_IdAndStatusNot(UUID projetoId, UUID profissionalId, StatusImagem status, Pageable pageable);
    Optional<ImagemAsset> findByIdAndProfissional_Id(UUID id, UUID profissionalId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ImagemAsset i WHERE i.id = :id AND i.profissional.id = :profissionalId")
    Optional<ImagemAsset> findOwnedForUpdate(@Param("id") UUID id, @Param("profissionalId") UUID profissionalId);
    boolean existsByLogoMarcaDagua_IdAndStatusNot(UUID imagemId, StatusImagem status);
}
