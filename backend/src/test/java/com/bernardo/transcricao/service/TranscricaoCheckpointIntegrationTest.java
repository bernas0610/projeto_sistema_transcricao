package com.bernardo.transcricao.service;

import com.bernardo.transcricao.exception.TranscriptionException;
import com.bernardo.transcricao.model.StatusTranscricao;
import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.provider.TranscriptionProvider;
import com.bernardo.transcricao.repository.TranscricaoParteRepository;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkpoint;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.admin.email=", "app.admin.senha="})
class TranscricaoCheckpointIntegrationTest {
    @Autowired TranscricaoRepository jobs;
    @Autowired TranscricaoParteRepository partes;
    @Autowired TranscricaoCheckpointService checkpoints;
    @MockitoBean AudioService audio;
    @MockitoBean TranscriptionProvider provider;
    @TempDir Path tmp;

    @BeforeEach
    void limpar() {
        partes.deleteAll();
        jobs.deleteAll();
        reset(audio, provider);
        when(audio.getDuracaoParteSegundos()).thenReturn(900);
    }

    @Test
    void retomaComCheckpointPersistidoSemRepetirGeminiEComMesmaDuracao() throws Exception {
        var job = criarJob();
        var primeira = tmp.resolve("parte_000.mp3");
        var segunda = tmp.resolve("parte_001.mp3");
        when(audio.dividir(any(), any(), eq(900))).thenReturn(List.of(primeira, segunda));
        when(provider.transcrever(primeira, "audio/mp3")).thenReturn("Primeiro trecho.");
        when(provider.transcrever(segunda, "audio/mp3")).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Interrompido");
        });
        try {
            processador().processar(job.getId());
        } finally {
            Thread.interrupted();
        }
        var interrompido = jobs.findById(job.getId()).orElseThrow();
        assertEquals(StatusTranscricao.PROCESSANDO, interrompido.getStatus());
        assertEquals(2, interrompido.getTotalPartes());
        assertEquals(1, interrompido.getPartesConcluidas());
        assertEquals("Primeiro trecho.", partes.findAllByTranscricaoIdOrderByNumeroAsc(job.getId()).getFirst().getTexto());
        assertTrue(Files.exists(Path.of(job.getCaminhoArquivo())));

        reset(provider);
        when(audio.getDuracaoParteSegundos()).thenReturn(1800);
        when(provider.transcrever(segunda, "audio/mp3")).thenReturn("Segundo trecho.");
        processador().processar(job.getId());

        var concluido = jobs.findById(job.getId()).orElseThrow();
        assertEquals(StatusTranscricao.CONCLUIDA, concluido.getStatus());
        assertEquals("Primeiro trecho.\n\nSegundo trecho.", concluido.getTexto());
        assertEquals(2, concluido.getPartesConcluidas());
        assertEquals(900, concluido.getDuracaoParteSegundos());
        assertEquals(2, partes.countByTranscricaoId(job.getId()));
        verify(provider, never()).transcrever(primeira, "audio/mp3");
        verify(provider).transcrever(segunda, "audio/mp3");
        verify(audio, times(2)).dividir(any(), any(), eq(900));
        assertFalse(Files.exists(Path.of(job.getCaminhoArquivo())));
    }

    @Test
    void falhaPreservaCheckpointEOriginal() throws Exception {
        var job = criarJob();
        var primeira = tmp.resolve("parte_000.mp3");
        var segunda = tmp.resolve("parte_001.mp3");
        when(audio.dividir(any(), any(), eq(900))).thenReturn(List.of(primeira, segunda));
        when(provider.transcrever(primeira, "audio/mp3")).thenReturn("Trecho salvo.");
        when(provider.transcrever(segunda, "audio/mp3")).thenThrow(new TranscriptionException("Falha no provedor"));
        processador().processar(job.getId());
        var falhou = jobs.findById(job.getId()).orElseThrow();
        assertEquals(StatusTranscricao.ERRO, falhou.getStatus());
        assertEquals(1, falhou.getPartesConcluidas());
        assertEquals(1, partes.countByTranscricaoId(job.getId()));
        assertNull(falhou.getTexto());
        assertTrue(Files.exists(Path.of(job.getCaminhoArquivo())));
    }

    @Test
    void consolidaTodasAsPartesSalvasSemChamarProvedor() throws Exception {
        var job = criarJob();
        job.setStatus(StatusTranscricao.PROCESSANDO);
        job.setDuracaoParteSegundos(900);
        job.setTotalPartes(2);
        jobs.saveAndFlush(job);
        checkpoints.salvar(job.getId(), 0, "Um.");
        checkpoints.salvar(job.getId(), 1, "Dois.");
        when(audio.dividir(any(), any(), eq(900))).thenReturn(
                List.of(tmp.resolve("parte_000.mp3"), tmp.resolve("parte_001.mp3")));
        processador().processar(job.getId());
        var concluido = jobs.findById(job.getId()).orElseThrow();
        assertEquals(StatusTranscricao.CONCLUIDA, concluido.getStatus());
        assertEquals("Um.\n\nDois.", concluido.getTexto());
        assertEquals(2, concluido.getPartesConcluidas());
        verifyNoInteractions(provider);
    }

    @Test
    void naoMisturaCheckpointComDivisaoIncompativel() throws Exception {
        var job = criarJob();
        job.setStatus(StatusTranscricao.PROCESSANDO);
        job.setDuracaoParteSegundos(900);
        job.setTotalPartes(2);
        jobs.saveAndFlush(job);
        checkpoints.salvar(job.getId(), 0, "Um.");
        when(audio.dividir(any(), any(), eq(900))).thenReturn(List.of(tmp.resolve("parte_000.mp3")));
        processador().processar(job.getId());
        var falhou = jobs.findById(job.getId()).orElseThrow();
        assertEquals(StatusTranscricao.ERRO, falhou.getStatus());
        assertEquals(com.bernardo.transcricao.exception.CodigoErro.CHECKPOINT_INCOMPATIVEL, falhou.getCodigoErro());
        assertEquals(1, falhou.getPartesConcluidas());
        assertEquals(1, partes.countByTranscricaoId(job.getId()));
        assertTrue(Files.exists(Path.of(job.getCaminhoArquivo())));
        verifyNoInteractions(provider);
    }

    private Transcricao criarJob() throws Exception {
        var job = new Transcricao();
        job.setNomeArquivoOriginal("teste.mp3");
        job.setCaminhoArquivo(Files.writeString(tmp.resolve("original.mp3"), "audio").toString());
        return jobs.saveAndFlush(job);
    }

    private TranscricaoProcessor processador() {
        // Synchronous entry for deterministic restart simulation, with real transactional checkpoints.
        return new TranscricaoProcessor(jobs, audio, provider, checkpoints);
    }
}
