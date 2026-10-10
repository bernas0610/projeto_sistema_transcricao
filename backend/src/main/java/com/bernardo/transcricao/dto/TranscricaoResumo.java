package com.bernardo.transcricao.dto;

import com.bernardo.transcricao.exception.CodigoErro;
import com.bernardo.transcricao.model.StatusTranscricao;
import java.time.Instant;
import java.util.UUID;

/** Metadados do histórico: não carrega o texto nem o caminho do original. */
public record TranscricaoResumo(UUID id, String nomeArquivoOriginal, StatusTranscricao status,
        String mensagemErro, CodigoErro codigoErro, boolean erroRepetivel,
        int totalPartes, int partesConcluidas, Instant criadoEm, Instant atualizadoEm) {
    public TranscricaoResumo(UUID id, String nomeArquivoOriginal, StatusTranscricao status,
            String mensagemErro, CodigoErro codigoErro, int totalPartes, int partesConcluidas,
            Instant criadoEm, Instant atualizadoEm) {
        this(id, nomeArquivoOriginal, status, mensagemErro, codigoErro,
                codigoErro != null && codigoErro.repetivel(), totalPartes, partesConcluidas,
                criadoEm, atualizadoEm);
    }
}
