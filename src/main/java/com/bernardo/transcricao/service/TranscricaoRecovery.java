package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.StatusTranscricao;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Recuperação na inicialização; pressupõe uma única instância da aplicação. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscricaoRecovery {

    private final TranscricaoRepository repository;
    private final TranscricaoProcessor processor;

    @EventListener(ApplicationReadyEvent.class)
    public void recuperar() {
        List<UUID> ids = repository.buscarIdsPorStatus(
                List.of(StatusTranscricao.PENDENTE, StatusTranscricao.PROCESSANDO));
        log.info("Recuperação: {} transcrições aguardando reprocessamento", ids.size());
        for (UUID id : ids) {
            processor.processar(id);
        }
    }
}
