package com.bernardo.transcricao.repository;

import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.model.StatusTranscricao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscricaoRepository extends JpaRepository<Transcricao, UUID> {
    Optional<Transcricao> findByIdAndUsuarioId(UUID id, UUID usuarioId);
    @Query("select t.id from Transcricao t where t.status in :status order by t.criadoEm, t.id")
    List<UUID> buscarIdsPorStatus(@Param("status") Collection<StatusTranscricao> status);
}
