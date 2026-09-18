package com.kinkan.take_too.security;

import com.kinkan.take_too.domain.entity.Cliente;
import com.kinkan.take_too.domain.entity.Profissional;
import lombok.Getter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails {

    @NonNull
    private final UUID id;
    private final String username;
    private final String password;
    private final String role;
    @org.jspecify.annotations.Nullable
    private final UUID projetoIdAcesso; // Apenas para clientes

    // Construtor para o Profissional
    public CustomUserDetails(Profissional profissional) {
        this.id = Objects.requireNonNull(profissional.getId(), "Profissional ID não pode ser nulo");
        this.username = profissional.getEmail();
        this.password = profissional.getSenhaHash();
        this.role = "ROLE_PROFISSIONAL";
        this.projetoIdAcesso = null;
    }

    // Construtor para o Cliente Final
    public CustomUserDetails(Cliente cliente, UUID projetoIdAcesso) {
        this.id = Objects.requireNonNull(cliente.getId(), "Cliente ID não pode ser nulo");
        this.username = cliente.getId().toString();
        this.password = ""; // Cliente não possui senha
        this.role = "ROLE_CLIENTE";
        this.projetoIdAcesso = projetoIdAcesso;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(this.role));
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
