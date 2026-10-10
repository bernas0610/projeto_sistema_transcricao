package com.bernardo.transcricao.service;

import com.bernardo.transcricao.model.TranscricaoParte;
import com.bernardo.transcricao.repository.TranscricaoParteRepository;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TranscricaoCheckpointService {
    private final TranscricaoParteRepository partes;
    private final TranscricaoRepository transcricoes;

    public List<TranscricaoParte> listar(UUID id) {
        return partes.findAllByTranscricaoIdOrderByNumeroAsc(id);
    }

    // Each confirmed segment and its progress counter commit together.
    // The provider call runs outside this transaction.
    @Transactional
    public int salvar(UUID id, int numero, String texto) {
        var job = transcricoes.findById(id).orElseThrow();
        var parte = new TranscricaoParte();
        parte.setTranscricaoId(id);
        parte.setNumero(numero);
        parte.setTexto(texto);
        partes.save(parte);
        int concluidas = Math.toIntExact(partes.countByTranscricaoId(id));
        job.setPartesConcluidas(concluidas);
        transcricoes.save(job);
        return concluidas;
    }
}
