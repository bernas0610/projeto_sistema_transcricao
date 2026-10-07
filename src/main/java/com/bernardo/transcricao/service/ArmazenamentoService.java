package com.bernardo.transcricao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class ArmazenamentoService {

    private static final Set<String> EXTENSOES_PERMITIDAS =
            Set.of("mp3", "wav", "m4a", "ogg", "flac", "aac", "webm", "opus", "mpeg");

    private final Path diretorio;

    public ArmazenamentoService(@Value("${app.armazenamento.diretorio}") String diretorio) {
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.diretorio);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar o diretório de uploads", e);
        }
    }

    public Path salvar(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo vazio");
        }

        String extensao = extrairExtensao(arquivo.getOriginalFilename());
        if (!EXTENSOES_PERMITIDAS.contains(extensao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato não suportado. Use: " + EXTENSOES_PERMITIDAS);
        }

        // Nome gerado pelo servidor: nunca usar o nome original no caminho (path traversal)
        Path destino = diretorio.resolve(UUID.randomUUID() + "." + extensao);
        try (InputStream in = arquivo.getInputStream()) {
            Files.copy(in, destino);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao salvar o arquivo", e);
        }
        return destino;
    }

    public void remover(Path caminho) {
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException ignored) {
            // melhor esforço: arquivo órfão não deve mascarar o erro original
        }
    }

    private String extrairExtensao(String nome) {
        if (nome == null || !nome.contains(".")) {
            return "";
        }
        return nome.substring(nome.lastIndexOf('.') + 1).toLowerCase();
    }
}