package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.model.StatusTranscricao;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TranscricaoRegistroService {
    private final TranscricaoRepository repository;
    private final CotaUsuarioService cotas;

    /** O upload e a reserva da cota são confirmados na mesma transação. */
    @Transactional
    public Transcricao registrar(String nome, Path caminho, UUID usuarioId) {
        cotas.reservarArquivo(usuarioId);
        Transcricao transcricao = new Transcricao();
        transcricao.setUsuarioId(usuarioId);
        transcricao.setNomeArquivoOriginal(nome.length() > 255 ? nome.substring(0, 255) : nome);
        transcricao.setCaminhoArquivo(caminho.toString());
        return repository.save(transcricao);
    }

    /** Locks the job until the ERRO -> PENDENTE transition commits. No new upload quota. */
    @Transactional
    public Transcricao prepararReprocessamento(UUID id, UUID usuarioId) {
        Transcricao job = repository.buscarParaReprocessar(id, usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcrição não encontrada"));
        if (job.getStatus() != StatusTranscricao.ERRO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Apenas transcrições com erro podem ser reprocessadas");
        }
        if (!Files.isRegularFile(Path.of(job.getCaminhoArquivo()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Áudio original não disponível para reprocessamento");
        }
        job.setStatus(StatusTranscricao.PENDENTE);
        job.setMensagemErro(null);
        job.setTexto(null);
        return repository.save(job);
    }
}
