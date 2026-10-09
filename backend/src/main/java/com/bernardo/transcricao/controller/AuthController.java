package com.bernardo.transcricao.controller;

import com.bernardo.transcricao.dto.CadastroRequest;
import com.bernardo.transcricao.dto.UsuarioResponse;
import com.bernardo.transcricao.model.Usuario;
import com.bernardo.transcricao.security.UsuarioPrincipal;
import com.bernardo.transcricao.service.CotaUsuarioService;
import com.bernardo.transcricao.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UsuarioService usuarios;
    private final CotaUsuarioService cotas;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@Valid @RequestBody CadastroRequest request) {
        Usuario usuario = usuarios.cadastrar(request);
        return cotas.consultar(usuario.getId());
    }

    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal UsuarioPrincipal usuario) {
        return cotas.consultar(usuario.id());
    }
}
