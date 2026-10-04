package com.medisphere.security;

import com.medisphere.domain.Provider;
import com.medisphere.repository.ProviderRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads provider by email for Spring Security's authentication manager.
 * Used during the login flow only — JWT filter does NOT use this service
 * (it reads claims directly from the token).
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final ProviderRepository providerRepository;

    public UserDetailsServiceImpl(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Provider provider = providerRepository.findByEmail(email)
                .filter(Provider::isActive)
                .orElseThrow(() -> new UsernameNotFoundException("Provider not found: " + email));

        return User.builder()
                .username(provider.getEmail())
                .password(provider.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + provider.getRole().name())))
                .accountLocked(!provider.isActive())
                .build();
    }
}
