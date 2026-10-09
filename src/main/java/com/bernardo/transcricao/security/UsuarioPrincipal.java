package com.bernardo.transcricao.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record UsuarioPrincipal(UUID id, String email, String senhaHash) implements UserDetails {
    @Override
    public String getUsername() { return email; }

    @Override
    public String getPassword() { return senhaHash; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
