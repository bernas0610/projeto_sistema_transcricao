package com.bernardo.transcricao.repository;

import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.model.StatusTranscricao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TranscricaoRepository extends JpaRepository<Transcricao, UUID> {
    @Query("select new com.bernardo.transcricao.dto.TranscricaoResumo(t.id, t.nomeArquivoOriginal, "
            + "t.status, t.mensagemErro, t.codigoErro, t.totalPartes, t.partesConcluidas, "
            + "t.criadoEm, t.atualizadoEm) from Transcricao t where t.usuarioId = :usuarioId")
    Page<com.bernardo.transcricao.dto.TranscricaoResumo> listarResumos(
            @Param("usuarioId") UUID usuarioId, Pageable pageable);
    Optional<Transcricao> findByIdAndUsuarioId(UUID id, UUID usuarioId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transcricao t where t.id = :id and t.usuarioId = :usuarioId")
    Optional<Transcricao> buscarParaReprocessar(@Param("id") UUID id,
            @Param("usuarioId") UUID usuarioId);
    @Query("select t.id from Transcricao t where t.status in :status order by t.criadoEm, t.id")
    List<UUID> buscarIdsPorStatus(@Param("status") Collection<StatusTranscricao> status);
}
