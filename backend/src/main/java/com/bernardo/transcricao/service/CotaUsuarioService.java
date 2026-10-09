package com.bernardo.transcricao.service;

import com.bernardo.transcricao.dto.UsuarioResponse;
import com.bernardo.transcricao.model.Usuario;
import com.bernardo.transcricao.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class CotaUsuarioService {
    private final UsuarioRepository repository;
    private final int limite;
    private final Clock clock;

    @Autowired
    public CotaUsuarioService(UsuarioRepository repository,
            @Value("${app.uso.limite-arquivos-diario:5}") int limite,
            @Value("${app.uso.fuso:America/Sao_Paulo}") String fuso) {
        this(repository, limite, Clock.system(ZoneId.of(fuso)));
    }

    CotaUsuarioService(UsuarioRepository repository, int limite, Clock clock) {
        if (limite < 1) { throw new IllegalArgumentException("Limite diário deve ser positivo"); }
        this.repository = repository;
        this.limite = limite;
        this.clock = clock;
    }

    @Transactional
    public void reservarArquivo(UUID usuarioId) {
        Usuario usuario = repository.buscarParaReserva(usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));
        LocalDate hoje = LocalDate.now(clock);
        int usadas = usadasHoje(usuario, hoje);
        if (usadas >= limite) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite diário de " + limite + " arquivos atingido. Tente novamente amanhã.");
        }
        usuario.setDiaUso(hoje);
        usuario.setArquivosUsados(usadas + 1);
        repository.save(usuario);
    }

    public UsuarioResponse consultar(UUID id) {
        Usuario usuario = repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));
        return new UsuarioResponse(id, usuario.getEmail(), usuario.getRole(), limite, usadasHoje(usuario, LocalDate.now(clock)));
    }

    private int usadasHoje(Usuario usuario, LocalDate hoje) {
        return hoje.equals(usuario.getDiaUso()) ? usuario.getArquivosUsados() : 0;
    }
}
