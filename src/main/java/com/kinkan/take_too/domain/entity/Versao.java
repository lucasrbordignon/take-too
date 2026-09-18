package com.kinkan.take_too.domain.entity;

import com.kinkan.take_too.domain.enums.StatusVersao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(nullable = false, length = 1024)
    private String arquivoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVersao status;

    @OneToMany(mappedBy = "versao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comentario> comentarios = new ArrayList<>();
}
