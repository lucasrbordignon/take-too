package com.kinkan.take_too.domain.entity;

import com.kinkan.take_too.domain.enums.EtapaProjeto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
public class Projeto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtapaProjeto etapaAtual;

    @Column(nullable = false)
    private boolean magicLinkAtivo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private java.time.Instant criadoEm = java.time.Instant.now();

    @Column(name = "atualizado_em", nullable = false)
    private java.time.Instant atualizadoEm = java.time.Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = java.time.Instant.now();
    }

    @OneToMany(mappedBy = "projeto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Versao> versoes = new ArrayList<>();
}
