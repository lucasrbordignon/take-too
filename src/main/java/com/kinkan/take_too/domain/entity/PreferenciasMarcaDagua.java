package com.kinkan.take_too.domain.entity;

import com.kinkan.take_too.domain.dto.MarcaDaguaDTO.Posicao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profissional_marca_dagua")
@Getter
@Setter
public class PreferenciasMarcaDagua {
    @Id
    @Column(name = "profissional_id")
    private UUID profissionalId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_imagem_id")
    private ImagemAsset logo;

    @Column(nullable = false)
    private boolean ativaPadrao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Posicao posicao = Posicao.INFERIOR_DIREITA;

    @Column(nullable = false)
    private int larguraPercentual = 15;
    @Column(nullable = false)
    private int opacidadePercentual = 70;
    @Column(nullable = false)
    private int margemPercentual = 3;
    @Column(nullable = false)
    private Instant atualizadoEm = Instant.now();
}
