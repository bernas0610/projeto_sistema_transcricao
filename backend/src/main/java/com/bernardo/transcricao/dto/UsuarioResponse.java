package com.bernardo.transcricao.dto;

import java.util.UUID;
import com.bernardo.transcricao.model.RoleUsuario;

public record UsuarioResponse(UUID id, String email, RoleUsuario role, int limiteArquivosDiario, int arquivosEnviadosHoje) {
}
