package com.bernardo.transcricao.controller;

import com.bernardo.transcricao.dto.TranscricaoResponse;
import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.service.TranscricaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/transcricoes")
@RequiredArgsConstructor
public class TranscricaoController {

    private final TranscricaoService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TranscricaoResponse> criar(@RequestParam("arquivo") MultipartFile arquivo) {
        Transcricao transcricao = service.criar(arquivo);
        return ResponseEntity
                .accepted()
                .location(URI.create("/transcricoes/" + transcricao.getId()))
                .body(TranscricaoResponse.from(transcricao));
    }

    @GetMapping("/{id}")
    public TranscricaoResponse buscar(@PathVariable UUID id) {
        return TranscricaoResponse.from(service.buscar(id));
    }
}
