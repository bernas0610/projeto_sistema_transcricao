package com.bernardo.transcricao.dto;

import java.util.UUID;

public record UsuarioResponse(UUID id, String email, int limiteArquivosDiario, int arquivosEnviadosHoje) {
}
