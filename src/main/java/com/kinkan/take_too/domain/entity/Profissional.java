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
public class Profissional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String senhaHash;

    @Column(nullable = false)
    private String plano;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "profissional_cliente",
        joinColumns = @JoinColumn(name = "profissional_id"),
        inverseJoinColumns = @JoinColumn(name = "cliente_id")
    )
    private List<Cliente> clientes = new ArrayList<>();
}
