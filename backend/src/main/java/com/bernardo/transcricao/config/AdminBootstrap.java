package com.bernardo.transcricao.config;

import com.bernardo.transcricao.dto.CadastroRequest;
import com.bernardo.transcricao.model.RoleUsuario;
import com.bernardo.transcricao.model.Usuario;
import com.bernardo.transcricao.repository.UsuarioRepository;
import com.bernardo.transcricao.service.UsuarioService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Cria o primeiro administrador a partir de credenciais locais, sem endpoint público. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UsuarioRepository repository;
    private final UsuarioService usuarios;
    private final Validator validator;
    private final String email;
    private final String senha;

    public AdminBootstrap(UsuarioRepository repository, UsuarioService usuarios, Validator validator,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.senha:}") String senha) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.validator = validator;
        this.email = email;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && senha.isBlank()) { return; }
        if (repository.existsByRole(RoleUsuario.ADMIN)) { return; }
        CadastroRequest request = new CadastroRequest(email.strip().toLowerCase(Locale.ROOT), senha);
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Configure ADMIN_EMAIL válido e ADMIN_PASSWORD com 8 a 72 caracteres");
        }
        if (repository.findByEmail(request.email()).isPresent()) {
            throw new IllegalStateException("O e-mail do primeiro admin já pertence a outra conta; use um e-mail novo");
        }
        Usuario admin = usuarios.cadastrar(request);
        admin.setRole(RoleUsuario.ADMIN);
        repository.saveAndFlush(admin);
    }
}
