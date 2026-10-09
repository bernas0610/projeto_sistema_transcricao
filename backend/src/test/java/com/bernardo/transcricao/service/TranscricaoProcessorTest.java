package com.bernardo.transcricao.service;

import com.bernardo.transcricao.exception.TranscriptionException;
import com.bernardo.transcricao.model.StatusTranscricao;
import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.provider.TranscriptionProvider;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TranscricaoProcessorTest {
    @TempDir
    Path tmp;

    private final TranscricaoRepository repository = mock(TranscricaoRepository.class);
    private final AudioService audioService = mock(AudioService.class);
    private final TranscriptionProvider provider = mock(TranscriptionProvider.class);
    private final TranscricaoProcessor processor = new TranscricaoProcessor(repository, audioService, provider);

    @Test
    void recuperaProcessandoLimpaPartesAntigasEConclui() throws Exception {
        Transcricao job = job(StatusTranscricao.PROCESSANDO);
        Path original = Path.of(job.getCaminhoArquivo());
        Files.writeString(original, "audio");
        Path pasta = original.resolveSibling("partes-" + job.getId());
        Files.createDirectories(pasta);
        Path antiga = Files.writeString(pasta.resolve("parte_999.mp3"), "incompleta");
        Path parte = pasta.resolve("parte_000.mp3");
        when(audioService.dividir(original, pasta)).thenAnswer(invocation -> {
            assertFalse(Files.exists(antiga));
            Files.createDirectories(pasta);
            Files.writeString(parte, "audio");
            return List.of(parte);
        });
        when(provider.transcrever(parte, "audio/mp3")).thenReturn("Texto recuperado");
        job.setMensagemErro("erro antigo");
        job.setTexto("texto antigo");

        processor.processar(job.getId());

        assertEquals(StatusTranscricao.CONCLUIDA, job.getStatus());
        assertEquals("Texto recuperado", job.getTexto());
        assertNull(job.getMensagemErro());
        assertFalse(Files.exists(original));
        assertFalse(Files.exists(pasta));
        verify(repository, times(2)).save(job);
    }

    @Test
    void marcaErroQuandoOriginalNaoExiste() {
        Transcricao job = job(StatusTranscricao.PENDENTE);
        processor.processar(job.getId());
        assertEquals(StatusTranscricao.ERRO, job.getStatus());
        assertTrue(job.getMensagemErro().contains("original não encontrado"));
        verifyNoInteractions(audioService, provider);
    }

    @Test
    void naoReprocessaEstadosFinais() {
        for (StatusTranscricao status : List.of(StatusTranscricao.CONCLUIDA, StatusTranscricao.ERRO)) {
            Transcricao job = job(status);
            processor.processar(job.getId());
            assertEquals(status, job.getStatus());
        }
        verify(repository, never()).save(any());
        verifyNoInteractions(audioService, provider);
    }

    @Test
    void preservaOriginalQuandoProvedorFalha() throws Exception {
        Transcricao job = job(StatusTranscricao.PENDENTE);
        Path original = Files.writeString(Path.of(job.getCaminhoArquivo()), "audio");
        Path parte = tmp.resolve("parte.mp3");
        when(audioService.dividir(eq(original), any())).thenReturn(List.of(parte));
        when(provider.transcrever(parte, "audio/mp3")).thenThrow(new TranscriptionException("Falha no Gemini"));

        processor.processar(job.getId());

        assertEquals(StatusTranscricao.ERRO, job.getStatus());
        assertEquals("Falha no Gemini", job.getMensagemErro());
        assertTrue(Files.exists(original));
    }

    @Test
    void interrupcaoPreservaJobParaProximoInicio() throws Exception {
        Transcricao job = job(StatusTranscricao.PENDENTE);
        Path original = Files.writeString(Path.of(job.getCaminhoArquivo()), "audio");
        when(audioService.dividir(eq(original), any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Processamento interrompido");
        });
        try {
            processor.processar(job.getId());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(StatusTranscricao.PROCESSANDO, job.getStatus());
            assertTrue(Files.exists(original));
            assertNull(job.getMensagemErro());
            verify(repository).save(job);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void ignoraJobInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        processor.processar(id);
        verifyNoInteractions(audioService, provider);
        verify(repository, never()).save(any());
    }

    private Transcricao job(StatusTranscricao status) {
        Transcricao job = new Transcricao();
        job.setId(UUID.randomUUID());
        job.setStatus(status);
        job.setCaminhoArquivo(tmp.resolve(job.getId() + ".mp3").toString());
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }
}
