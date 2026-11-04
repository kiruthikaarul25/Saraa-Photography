package com.shop.config;

import com.shop.model.Owner;
import com.shop.repository.OwnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class CustomUserDetailsService
        implements UserDetailsService {

    @Autowired
    private OwnerRepository ownerRepository;

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        // "admin" / "niki" are accepted as shortcuts for the owner's email
        String login = email == null ? "" : email.trim();
        if (login.equalsIgnoreCase("admin") || login.equalsIgnoreCase("niki"))
            login = "niki@gmail.com";

        Owner owner = ownerRepository
            .findByEmail(login)
            .orElseThrow(() ->
                new UsernameNotFoundException(
                    "Owner not found: " + email));

        return new User(
            owner.getEmail(),
            owner.getPasswordHash(),
            Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_OWNER"))
        );
    }
}