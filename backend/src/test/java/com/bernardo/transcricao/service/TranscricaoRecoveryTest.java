package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.StatusTranscricao;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class TranscricaoRecoveryTest {
    private final TranscricaoRepository repository = mock(TranscricaoRepository.class);
    private final TranscricaoProcessor processor = mock(TranscricaoProcessor.class);
    private final TranscricaoRecovery recovery = new TranscricaoRecovery(repository, processor);

    @Test
    void reenfileiraPendentesEProcessandoNaOrdemDoBanco() {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(repository.buscarIdsPorStatus(List.of(StatusTranscricao.PENDENTE, StatusTranscricao.PROCESSANDO)))
                .thenReturn(List.of(primeiro, segundo));

        recovery.recuperar();

        InOrder ordem = inOrder(processor);
        ordem.verify(processor).processar(primeiro);
        ordem.verify(processor).processar(segundo);
        verifyNoMoreInteractions(processor);
    }

    @Test
    void naoEnfileiraQuandoNaoHaJobsInterrompidos() {
        when(repository.buscarIdsPorStatus(anyCollection())).thenReturn(List.of());
        recovery.recuperar();
        verifyNoInteractions(processor);
    }
}
