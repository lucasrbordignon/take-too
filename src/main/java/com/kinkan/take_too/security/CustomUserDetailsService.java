package com.kinkan.take_too.security;

import com.kinkan.take_too.repository.ProfissionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final ProfissionalRepository profissionalRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return profissionalRepository.findByEmail(username)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Profissional não encontrado com email: " + username));
    }
}
