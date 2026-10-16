package com.kinkan.take_too.domain.entity;

import com.kinkan.take_too.domain.enums.StatusVideo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class VideoAsset {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projeto_rascunho_id")
    private Projeto projetoRascunho;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 50)
    private String provedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusVideo status;

    @Column(length = 255)
    private String uploadExternoId;
    @Column(length = 255)
    private String assetExternoId;
    @Column(length = 255)
    private String playbackExternoId;

    private Double duracaoSegundos;
    private Integer largura;
    private Integer altura;

    @Column(length = 50)
    private String proporcao;

    @Column(length = 1024)
    private String erro;

    @Column(columnDefinition = "TEXT")
    private String marcaDaguaJson;

    private Instant uploadExpiraEm;

    @Column(nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
    @Column(nullable = false)
    private Instant atualizadoEm = Instant.now();
    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_marca_dagua_id")
    private ImagemAsset logoMarcaDagua;

    @Nullable
    @Column(columnDefinition = "TEXT")
    private String marcaDaguaPreferenciasJson;
}
