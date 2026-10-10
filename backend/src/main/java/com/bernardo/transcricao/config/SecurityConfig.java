package com.bernardo.transcricao.config;

import com.bernardo.transcricao.repository.UsuarioRepository;
import com.bernardo.transcricao.security.UsuarioPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Locale;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(UsuarioRepository repository) {
        return email -> repository.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(u -> new UsuarioPrincipal(u.getId(), u.getEmail(), u.getSenhaHash(), u.getRole()))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/cadastro").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                com.bernardo.transcricao.dto.ErroResponse.escrever(response, 401))
                        .accessDeniedHandler((request, response, error) ->
                                com.bernardo.transcricao.dto.ErroResponse.escrever(response, 403)))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.loginProcessingUrl("/auth/login")
                        .usernameParameter("email")
                        .successHandler((request, response, auth) -> response.setStatus(204))
                        .failureHandler((request, response, error) ->
                                com.bernardo.transcricao.dto.ErroResponse.escrever(response, 401)))
                .logout(logout -> logout.logoutUrl("/auth/logout")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .build();
    }
}
