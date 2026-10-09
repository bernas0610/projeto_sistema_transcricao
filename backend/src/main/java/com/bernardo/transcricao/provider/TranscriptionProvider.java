package com.bernardo.transcricao.provider;

import java.nio.file.Path;

public interface TranscriptionProvider {

    /** Transcreve um arquivo de áudio e retorna o texto. */
    String transcrever(Path audio, String mimeType);
}