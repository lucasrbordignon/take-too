package com.kinkan.take_too.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.kinkan.take_too.repository.ClienteRepository;
import com.kinkan.take_too.repository.ProfissionalRepository;
import com.kinkan.take_too.repository.ProjetoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final ProfissionalRepository profissionalRepository;
    private final ClienteRepository clienteRepository;
    private final ProjetoRepository projetoRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null) {
            DecodedJWT jwt = tokenService.decodeToken(token);
            if (jwt != null) {
                String role = jwt.getClaim("role").asString();
                UserDetails userDetails = null;

                if ("ROLE_CLIENTE".equals(role)) {
                    String subject = jwt.getSubject();
                    String projetoIdStr = jwt.getClaim("projetoId").asString();
                    
                    if (subject != null && projetoIdStr != null) {
                        UUID clienteId = UUID.fromString(subject);
                        UUID projetoId = UUID.fromString(projetoIdStr);

                        if (projetoId != null && clienteId != null) {
                            var projetoOpt = projetoRepository.findById(projetoId);
                            if (projetoOpt.isPresent() && projetoOpt.get().isMagicLinkAtivo()) {
                                var projeto = projetoOpt.get();
                                var clienteOpt = clienteRepository.findById(clienteId);
                                if (clienteOpt.isPresent() && projeto.getCliente() != null
                                        && clienteId.equals(projeto.getCliente().getId())) {
                                    userDetails = new CustomUserDetails(clienteOpt.get(), projetoId);
                                }
                            }
                        }
                    }
                } else {
                    // Trata por padrão como ROLE_PROFISSIONAL (compatibilidade com tokens antigos)
                    String email = jwt.getSubject();
                    if (email != null) {
                        var profissionalOpt = profissionalRepository.findByEmail(email);
                        if (profissionalOpt.isPresent()) {
                            userDetails = new CustomUserDetails(profissionalOpt.get());
                        }
                    }
                }

                if (userDetails != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                            userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null)
            return null;
        return authHeader.replace("Bearer ", "");
    }
}
