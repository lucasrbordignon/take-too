package com.kinkan.take_too.domain.entity;

import com.kinkan.take_too.domain.enums.StatusImagem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class ImagemAsset {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;
    @Nullable @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projeto_rascunho_id")
    private Projeto projetoRascunho;
    @Column(nullable = false) private String nome;
    @Column(nullable = false, length = 50) private String provedor;
    @Column(nullable = false, length = 512) private String chave;
    @Column(nullable = false, length = 100) private String tipo;
    @Column(nullable = false, length = 44) private String checksum;
    @Column(nullable = false) private long tamanhoBytes;
    @Nullable private Integer largura;
    @Nullable private Integer altura;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StatusImagem status;
    @Nullable @Column(length = 512) private String erro;
    @Column(nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(nullable = false) private Instant atualizadoEm = Instant.now();
    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_marca_dagua_id")
    private ImagemAsset logoMarcaDagua;

    @Nullable
    @Column(columnDefinition = "TEXT")
    private String marcaDaguaPreferenciasJson;
}
