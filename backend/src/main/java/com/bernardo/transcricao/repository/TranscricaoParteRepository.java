package com.bernardo.transcricao.repository;

import com.bernardo.transcricao.model.TranscricaoParte;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TranscricaoParteRepository extends JpaRepository<TranscricaoParte, Long> {
    List<TranscricaoParte> findAllByTranscricaoIdOrderByNumeroAsc(UUID transcricaoId);
    long countByTranscricaoId(UUID transcricaoId);
}
