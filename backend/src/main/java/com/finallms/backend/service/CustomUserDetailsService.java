package com.finallms.backend.service;

import com.finallms.backend.entity.User;
import com.finallms.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Here username can be email (for Admin) or phone (for Student)
        // Since we handle authentication manually in AuthService mostly,
        // this is primarily for Spring Security context if needed or for standard
        // flows.
        // We check both email and phone.
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhone(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + username));

        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new ArrayList<>();
        if (user.getRole() != null) {
            authorities
                    .add(new org.springframework.security.core.authority.SimpleGrantedAuthority(user.getRole().name()));
        }

        return new org.springframework.security.core.userdetails.User(
                username,
                user.getPassword() != null ? user.getPassword() : "", // Student has no password
                authorities);
    }
}
