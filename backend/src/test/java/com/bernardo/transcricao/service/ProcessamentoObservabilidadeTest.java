package com.bernardo.transcricao.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.exception.CodigoErro;
import com.bernardo.transcricao.exception.TranscriptionException;
import com.bernardo.transcricao.provider.TranscriptionProvider;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessamentoObservabilidadeTest {
    @TempDir Path tmp;
    @Test void correlacionaFalhaSemVazarAudioECredenciaisELimpaContexto() throws Exception {
        var repository = mock(TranscricaoRepository.class); var audio = mock(AudioService.class);
        var provider = mock(TranscriptionProvider.class); var checkpoints = mock(TranscricaoCheckpointService.class);
        var job = new Transcricao(); job.setId(UUID.randomUUID());
        job.setCaminhoArquivo(Files.writeString(tmp.resolve("SEGREDO.mp3"), "audio privado").toString());
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(audio.dividir(any(), any(), anyInt())).thenReturn(List.of(tmp.resolve("parte.mp3")));
        when(provider.transcrever(any(), any())).thenThrow(new TranscriptionException(CodigoErro.LIMITE_PROVEDOR,
                new RuntimeException("API_KEY segredo")));
        Logger logger = (Logger) LoggerFactory.getLogger(TranscricaoProcessor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
        try {
            new TranscricaoProcessor(repository, audio, provider, checkpoints).processar(job.getId());
            String logs = String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
            assertTrue(logs.contains("evento=job_inicio")); assertTrue(logs.contains("evento=parte_inicio"));
            assertTrue(logs.contains("codigo=LIMITE_PROVEDOR")); assertTrue(logs.contains("duracao_ms="));
            assertFalse(logs.contains("SEGREDO")); assertFalse(logs.contains("API_KEY"));
            assertTrue(appender.list.stream().allMatch(e -> e.getThrowableProxy() == null));
            assertTrue(appender.list.stream().anyMatch(e -> job.getId().toString().equals(e.getMDCPropertyMap().get("job_id"))));
            assertNull(MDC.get("job_id")); assertNull(MDC.get("parte"));
            assertEquals(CodigoErro.LIMITE_PROVEDOR, job.getCodigoErro());
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
