package com.kinkan.take_too.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class Comentario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "versao_id", nullable = false)
    private Versao versao;

    @org.jspecify.annotations.Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente clienteAutor;

    @Column(nullable = false)
    private Integer timestampSegundos;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(nullable = false)
    private Boolean resolvido = false;
}
