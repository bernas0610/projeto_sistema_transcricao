package com.bernardo.transcricao.controller;

import com.bernardo.transcricao.dto.TranscricaoResponse;
import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.service.TranscricaoService;
import com.bernardo.transcricao.security.UsuarioPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<TranscricaoResponse> criar(@RequestParam("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal UsuarioPrincipal usuario) {
        Transcricao transcricao = service.criar(arquivo, usuario.id());
        return ResponseEntity
                .accepted()
                .location(URI.create("/transcricoes/" + transcricao.getId()))
                .body(TranscricaoResponse.from(transcricao));
    }

    @GetMapping("/{id}")
    public TranscricaoResponse buscar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal usuario) {
        return TranscricaoResponse.from(service.buscar(id, usuario.id()));
    }
}
