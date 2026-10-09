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
import com.bernardo.transcricao.dto.TranscricaoResponse;
import com.bernardo.transcricao.dto.TranscricoesPage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class TranscricaoService {

    private final TranscricaoRepository repository;
    private final ArmazenamentoService armazenamento;
    private final TranscricaoProcessor processor;
    private final TranscricaoRegistroService registro;

    public Transcricao criar(MultipartFile arquivo, UUID usuarioId) {
        Path caminho = armazenamento.salvar(arquivo);
        Transcricao salva;
        try {
            salva = registro.registrar(arquivo.getOriginalFilename(), caminho, usuarioId);
        } catch (RuntimeException e) {
            armazenamento.remover(caminho);
            throw e;
        }
        processor.processar(salva.getId()); // roda em segundo plano
        return salva;
    }

    public Transcricao buscar(UUID id, UUID usuarioId) {
        return repository.findByIdAndUsuarioId(id, usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcrição não encontrada"));
    }

    public TranscricoesPage listar(UUID usuarioId, int pagina) {
        if (pagina < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página deve ser positiva ou zero");
        }
        var resultado = repository.findAllByUsuarioId(usuarioId,
                PageRequest.of(pagina, 20, Sort.by(Sort.Direction.DESC, "criadoEm", "id")));
        return new TranscricoesPage(resultado.map(TranscricaoResponse::from).getContent(), pagina,
                resultado.getTotalPages(), resultado.getTotalElements());
    }

}
