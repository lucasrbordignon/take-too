package com.kinkan.take_too.repository;

import com.kinkan.take_too.domain.entity.VideoAsset;
import com.kinkan.take_too.domain.enums.StatusVideo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VideoAssetRepository extends JpaRepository<VideoAsset, UUID> {
    Page<VideoAsset> findByProfissional_Id(UUID profissionalId, Pageable pageable);
    Page<VideoAsset> findByProjetoRascunho_IdAndProfissional_IdAndStatusNot(UUID projetoId, UUID profissionalId,
            StatusVideo status, Pageable pageable);
    Optional<VideoAsset> findByIdAndProfissional_Id(UUID id, UUID profissionalId);
    Optional<VideoAsset> findByProvedorAndUploadExternoId(String provedor, String uploadExternoId);
    Optional<VideoAsset> findByProvedorAndAssetExternoId(String provedor, String assetExternoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VideoAsset v WHERE v.id = :id")
    Optional<VideoAsset> findByIdForUpdate(@Param("id") UUID id);
    boolean existsByLogoMarcaDagua_IdAndStatusNot(UUID imagemId, StatusVideo status);
}
