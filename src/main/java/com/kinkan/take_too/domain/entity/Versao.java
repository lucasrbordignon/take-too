package com.kinkan.take_too.domain.entity;

import com.kinkan.take_too.domain.enums.StatusVersao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
public class Versao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projeto_id", nullable = false)
    private Projeto projeto;

    @Column(nullable = false)
    private Integer numero;

    @Nullable
    @Column(length = 1024)
    private String arquivoUrl;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private VideoAsset video;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVersao status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private java.time.Instant criadoEm = java.time.Instant.now();

    @OneToMany(mappedBy = "versao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comentario> comentarios = new ArrayList<>();
}
