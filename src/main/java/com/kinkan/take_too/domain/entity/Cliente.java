package com.kinkan.take_too.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String telefone;

    @org.jspecify.annotations.Nullable
    @Column
    private String email;

    @ManyToMany(mappedBy = "clientes")
    private List<Profissional> profissionais = new ArrayList<>();
}
