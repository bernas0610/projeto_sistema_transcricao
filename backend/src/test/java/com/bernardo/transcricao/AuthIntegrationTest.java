package com.bernardo.transcricao;

import com.bernardo.transcricao.dto.CadastroRequest;
import com.bernardo.transcricao.config.AdminBootstrap;
import com.bernardo.transcricao.model.RoleUsuario;
import jakarta.validation.Validator;
import com.bernardo.transcricao.model.Transcricao;
import com.bernardo.transcricao.model.Usuario;
import com.bernardo.transcricao.provider.TranscriptionProvider;
import com.bernardo.transcricao.repository.TranscricaoRepository;
import com.bernardo.transcricao.repository.UsuarioRepository;
import com.bernardo.transcricao.service.TranscricaoProcessor;
import com.bernardo.transcricao.service.TranscricaoRegistroService;
import com.bernardo.transcricao.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.armazenamento.diretorio=target/test-uploads",
        "app.uso.limite-arquivos-diario=5",
        "app.admin.email=", "app.admin.senha="})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired TranscricaoRepository transcricoes;
    @Autowired TranscricaoRegistroService registro;
    @Autowired PasswordEncoder encoder;
    @Autowired Validator validator;
    @MockitoBean TranscriptionProvider provider;
    @MockitoBean TranscricaoProcessor processor;

    @BeforeEach
    void limparBanco() {
        transcricoes.deleteAll();
        usuarioRepository.deleteAll();
        reset(processor);
    }

    @AfterEach
    void limparUploadsDoTeste() throws Exception {
        Path diretorio = Path.of("target/test-uploads").toAbsolutePath().normalize();
        for (Transcricao transcricao : transcricoes.findAll()) {
            Path arquivo = Path.of(transcricao.getCaminhoArquivo()).toAbsolutePath().normalize();
            if (arquivo.startsWith(diretorio)) { Files.deleteIfExists(arquivo); }
        }
    }

    @Test
    void cadastroProtegeSenhaENormalizaEmail() throws Exception {
        mvc.perform(post("/auth/cadastro").session(loginAdmin()).with(csrf()).contentType("application/json")
                        .content("{\"email\":\"Teste@EXAMPLE.COM\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("email").value("teste@example.com"))
                .andExpect(jsonPath("limiteArquivosDiario").value(5))
                .andExpect(jsonPath("role").value("USER"))
                .andExpect(jsonPath("senhaHash").doesNotExist()).andExpect(jsonPath("senha").doesNotExist());
        Usuario salvo = usuarioRepository.findByEmail("teste@example.com").orElseThrow();
        assertNotEquals("senha12345", salvo.getSenhaHash());
        assertTrue(encoder.matches("senha12345", salvo.getSenhaHash()));
    }

    @Test
    void cadastroDuplicadoRetorna409() throws Exception {
        cadastrar("teste@example.com");
        mvc.perform(post("/auth/cadastro").session(loginAdmin()).with(csrf()).contentType("application/json")
                        .content("{\"email\":\"TESTE@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isConflict());
        assertEquals(2, usuarioRepository.count());
    }

    @Test
    void rejeitaSenhaCurtaESenhaAcimaDoLimiteEmBytes() throws Exception {
        MockHttpSession admin = loginAdmin();
        for (String senha : List.of("123", "á".repeat(40))) {
            mvc.perform(post("/auth/cadastro").session(admin).with(csrf()).contentType("application/json")
                            .content("{\"email\":\"teste@example.com\",\"senha\":\"" + senha + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void exigeLoginECsrf() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/transcricoes/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/cadastro").contentType("application/json")
                        .content("{\"email\":\"teste@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("token").isNotEmpty()).andExpect(jsonPath("headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void loginESaidaUsamSessao() throws Exception {
        cadastrar("teste@example.com");
        MockHttpSession sessao = login("TESTE@example.com");
        mvc.perform(get("/auth/me").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("email").value("teste@example.com"));
        mvc.perform(post("/auth/logout").session(sessao).with(csrf())).andExpect(status().isNoContent());
        assertTrue(sessao.isInvalid());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void senhaIncorretaRetorna401() throws Exception {
        cadastrar("teste@example.com");
        mvc.perform(post("/auth/login").with(csrf()).param("email", "teste@example.com")
                        .param("password", "incorreta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCsrfDaApiFuncionaNoLogin() throws Exception {
        cadastrar("teste@example.com");
        MvcResult inicio = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        var json = new tools.jackson.databind.ObjectMapper().readTree(inicio.getResponse().getContentAsString());
        MockHttpSession sessao = (MockHttpSession) inicio.getRequest().getSession(false);
        mvc.perform(post("/auth/login").session(sessao)
                        .header(json.path("headerName").asString(), json.path("token").asString())
                        .param("email", "teste@example.com").param("password", "senha12345"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/auth/me").session(sessao)).andExpect(status().isOk());
    }

    @Test
    void uploadVinculaDonoEBloqueiaSextoSemDeixarArquivoOrfao() throws Exception {
        Usuario usuario = cadastrar("teste@example.com");
        MockHttpSession sessao = login("teste@example.com");
        for (int i = 0; i < 5; i++) {
            mvc.perform(multipart("/transcricoes").file(new MockMultipartFile(
                            "arquivo", "aula.mp3", "audio/mpeg", new byte[]{1, 2, 3}))
                            .session(sessao).with(csrf()))
                    .andExpect(status().isAccepted());
        }
        long arquivosAntes;
        try (var arquivos = Files.list(Path.of("target/test-uploads"))) {
            arquivosAntes = arquivos.count();
        }
        mvc.perform(multipart("/transcricoes").file(new MockMultipartFile(
                        "arquivo", "sexta.mp3", "audio/mpeg", new byte[]{1}))
                        .session(sessao).with(csrf()))
                .andExpect(status().isTooManyRequests());
        try (var arquivos = Files.list(Path.of("target/test-uploads"))) {
            assertEquals(arquivosAntes, arquivos.count());
        }
        assertEquals(5, transcricoes.count());
        assertTrue(transcricoes.findAll().stream().allMatch(t -> usuario.getId().equals(t.getUsuarioId())));
        verify(processor, times(5)).processar(any());
        mvc.perform(get("/auth/me").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("arquivosEnviadosHoje").value(5));
    }

    @Test
    void arquivoInvalidoNaoConsomeCota() throws Exception {
        Usuario usuario = cadastrar("teste@example.com");
        mvc.perform(multipart("/transcricoes").file(new MockMultipartFile(
                        "arquivo", "texto.txt", "text/plain", new byte[]{1}))
                        .session(login("teste@example.com")).with(csrf()))
                .andExpect(status().isBadRequest());
        assertEquals(0, usuarioRepository.findById(usuario.getId()).orElseThrow().getArquivosUsados());
        verifyNoInteractions(processor);
    }

    @Test
    void usuarioNaoVeTranscricaoAlheiaOuLegada() throws Exception {
        Usuario dono = cadastrar("dono@example.com");
        cadastrar("outro@example.com");
        Transcricao privada = registro.registrar("aula.mp3", Path.of("target/aula.mp3"), dono.getId());
        Transcricao legada = new Transcricao();
        legada.setNomeArquivoOriginal("antiga.mp3");
        legada.setCaminhoArquivo("target/antiga.mp3");
        transcricoes.saveAndFlush(legada);
        MockHttpSession sessaoDono = login("dono@example.com");
        MockHttpSession sessaoOutro = login("outro@example.com");
        mvc.perform(get("/transcricoes/" + privada.getId()).session(sessaoDono)).andExpect(status().isOk());
        mvc.perform(get("/transcricoes/" + privada.getId()).session(sessaoOutro)).andExpect(status().isNotFound());
        mvc.perform(get("/transcricoes/" + legada.getId()).session(sessaoDono)).andExpect(status().isNotFound());
    }

    @Test
    void sextoArquivoBloqueadoEDiaNovoRenovaCota() {
        Usuario usuario = cadastrar("teste@example.com");
        for (int i = 0; i < 5; i++) {
            registro.registrar("aula.mp3", Path.of("target/aula.mp3"), usuario.getId());
        }
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> registro.registrar("sexta.mp3", Path.of("target/sexta.mp3"), usuario.getId()));
        assertEquals(429, erro.getStatusCode().value());
        assertEquals(5, transcricoes.count());
        Usuario recarregado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertEquals(5, recarregado.getArquivosUsados());
        recarregado.setDiaUso(LocalDate.of(2000, 1, 1));
        usuarioRepository.saveAndFlush(recarregado);
        registro.registrar("novo-dia.mp3", Path.of("target/novo-dia.mp3"), usuario.getId());
        assertEquals(1, usuarioRepository.findById(usuario.getId()).orElseThrow().getArquivosUsados());
    }

    @Test
    void falhaAoRegistrarNaoConsomeCota() {
        Usuario usuario = cadastrar("teste@example.com");
        assertThrows(RuntimeException.class, () -> registro.registrar(null, Path.of("target/aula.mp3"), usuario.getId()));
        assertEquals(0, usuarioRepository.findById(usuario.getId()).orElseThrow().getArquivosUsados());
        assertEquals(0, transcricoes.count());
    }

    @Test
    void uploadsConcorrentesNaoUltrapassamCincoArquivos() throws Exception {
        Usuario usuario = cadastrar("teste@example.com");
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            CountDownLatch inicio = new CountDownLatch(1);
            List<Future<Boolean>> tarefas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tarefas.add(pool.submit(() -> {
                    inicio.await();
                    try {
                        registro.registrar("aula.mp3", Path.of("target/aula.mp3"), usuario.getId());
                        return true;
                    } catch (ResponseStatusException e) {
                        assertEquals(429, e.getStatusCode().value());
                        return false;
                    }
                }));
            }
            inicio.countDown();
            int aceitos = 0;
            for (Future<Boolean> tarefa : tarefas) {
                if (tarefa.get(20, TimeUnit.SECONDS)) { aceitos++; }
            }
            assertEquals(5, aceitos);
        }
        assertEquals(5, transcricoes.count());
        assertEquals(5, usuarioRepository.findById(usuario.getId()).orElseThrow().getArquivosUsados());
    }

    private Usuario cadastrar(String email) {
        return usuarios.cadastrar(new CadastroRequest(email, "senha12345"));
    }

    private MockHttpSession loginAdmin() throws Exception {
        Usuario admin = cadastrar("admin@example.com");
        admin.setRole(RoleUsuario.ADMIN);
        usuarioRepository.saveAndFlush(admin);
        return login(admin.getEmail());
    }

    @Test
    void visitanteNaoPodeCriarUsuarioMesmoComCsrf() throws Exception {
        mvc.perform(post("/auth/cadastro").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"novo@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void usuarioComumNaoPodeCriarOutrosUsuarios() throws Exception {
        cadastrar("comum@example.com");
        MockHttpSession comum = login("comum@example.com");
        mvc.perform(post("/auth/cadastro").session(comum).with(csrf()).contentType("application/json")
                        .content("{\"email\":\"novo@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isForbidden());
        assertEquals(1, usuarioRepository.count());
        mvc.perform(get("/auth/me").session(comum)).andExpect(status().isOk())
                .andExpect(jsonPath("role").value("USER"));
    }

    @Test
    void adminNaoPodeCriarOutroAdminPeloCadastro() throws Exception {
        MockHttpSession admin = loginAdmin();
        mvc.perform(get("/auth/me").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("role").value("ADMIN"));
        mvc.perform(post("/auth/cadastro").session(admin).with(csrf()).contentType("application/json")
                        .content("{\"email\":\"novo@example.com\",\"senha\":\"senha12345\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("role").value("USER"));
        assertEquals(RoleUsuario.USER, usuarioRepository.findByEmail("novo@example.com").orElseThrow().getRole());
    }

    @Test
    void adminTambemPrecisaDeCsrfParaCadastrar() throws Exception {
        mvc.perform(post("/auth/cadastro").session(loginAdmin()).contentType("application/json")
                        .content("{\"email\":\"novo@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isForbidden());
        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void primeiroAdminTemSenhaProtegidaENaoERecriado() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(usuarioRepository, usuarios, validator,
                "Primeiro@example.com", "senha12345");
        bootstrap.run(null);
        Usuario admin = usuarioRepository.findByEmail("primeiro@example.com").orElseThrow();
        assertEquals(RoleUsuario.ADMIN, admin.getRole());
        assertTrue(encoder.matches("senha12345", admin.getSenhaHash()));
        new AdminBootstrap(usuarioRepository, usuarios, validator, "outro@example.com", "outrasenha123").run(null);
        assertEquals(1, usuarioRepository.count());
        assertEquals(admin.getSenhaHash(), usuarioRepository.findById(admin.getId()).orElseThrow().getSenhaHash());
        mvc.perform(get("/auth/me").session(login("primeiro@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("role").value("ADMIN"));
    }

    @Test
    void bootstrapNaoPromoveUsuarioComumExistente() {
        Usuario comum = cadastrar("comum@example.com");
        assertThrows(IllegalStateException.class, () -> new AdminBootstrap(usuarioRepository, usuarios, validator,
                comum.getEmail(), "senha12345").run(null));
        assertEquals(RoleUsuario.USER, usuarioRepository.findById(comum.getId()).orElseThrow().getRole());
    }

    @Test
    void bootstrapNaoCriaAdminSemConfiguracao() {
        new AdminBootstrap(usuarioRepository, usuarios, validator, "", "").run(null);
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void bootstrapRejeitaCredenciaisInvalidasSemCriarConta() {
        for (CadastroRequest request : List.of(new CadastroRequest("admin@example.com", ""),
                new CadastroRequest("invalido", "senha12345"),
                new CadastroRequest("admin@example.com", "123"))) {
            assertThrows(IllegalStateException.class, () -> new AdminBootstrap(usuarioRepository, usuarios, validator,
                    request.email(), request.senha()).run(null));
        }
        assertEquals(0, usuarioRepository.count());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").with(csrf()).param("email", email)
                        .param("password", "senha12345"))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
