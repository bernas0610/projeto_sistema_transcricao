package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.Transcricao;
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
}
