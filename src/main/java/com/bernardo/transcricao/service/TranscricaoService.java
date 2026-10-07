package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TranscricaoService {

    private final TranscricaoRepository repository;
    private final ArmazenamentoService armazenamento;

    public Transcricao criar(MultipartFile arquivo) {
        Path caminho = armazenamento.salvar(arquivo);
        try {
            Transcricao transcricao = new Transcricao();
            transcricao.setNomeArquivoOriginal(limitar(arquivo.getOriginalFilename(), 255));
            transcricao.setCaminhoArquivo(caminho.toString());
            return repository.save(transcricao);
        } catch (RuntimeException e) {
            armazenamento.remover(caminho);
            throw e;
        }
    }

    public Transcricao buscar(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcrição não encontrada"));
    }

    private String limitar(String texto, int max) {
        return texto.length() > max ? texto.substring(0, max) : texto;
    }
}
