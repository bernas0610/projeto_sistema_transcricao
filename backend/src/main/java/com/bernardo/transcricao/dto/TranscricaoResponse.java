package com.bernardo.transcricao.dto;

import com.bernardo.transcricao.model.StatusTranscricao;
import com.bernardo.transcricao.model.Transcricao;

import java.time.Instant;
import java.util.UUID;

public record TranscricaoResponse(
        UUID id,
        String nomeArquivoOriginal,
        StatusTranscricao status,
        String texto,
        String mensagemErro,
        int totalPartes,
        int partesConcluidas,
        Instant criadoEm,
        Instant atualizadoEm
) {
    public static TranscricaoResponse from(Transcricao t) {
        return new TranscricaoResponse(
                t.getId(),
                t.getNomeArquivoOriginal(),
                t.getStatus(),
                t.getTexto(),
                t.getMensagemErro(),
                t.getTotalPartes(),
                t.getPartesConcluidas(),
                t.getCriadoEm(),
                t.getAtualizadoEm()
        );
    }
}
