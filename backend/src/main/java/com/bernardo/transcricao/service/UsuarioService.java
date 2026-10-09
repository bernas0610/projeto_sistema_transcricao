package com.bernardo.transcricao.service;

import com.bernardo.transcricao.dto.CadastroRequest;
import com.bernardo.transcricao.model.Usuario;
import com.bernardo.transcricao.model.RoleUsuario;
import com.bernardo.transcricao.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;

    public Usuario cadastrar(CadastroRequest request) {
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha deve ter no máximo 72 bytes UTF-8");
        }
        Usuario usuario = new Usuario();
        usuario.setRole(RoleUsuario.USER);
        usuario.setEmail(request.email().strip().toLowerCase(Locale.ROOT));
        usuario.setSenhaHash(encoder.encode(request.senha()));
        try {
            return repository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
    }
}
