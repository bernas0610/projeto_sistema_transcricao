package com.bernardo.transcricao.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "transcricao_parte", uniqueConstraints =
        @UniqueConstraint(name = "uk_transcricao_parte", columnNames = {"transcricao_id", "numero"}))
@Getter
@Setter
@NoArgsConstructor
public class TranscricaoParte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transcricao_id", nullable = false)
    private UUID transcricaoId;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;
}
