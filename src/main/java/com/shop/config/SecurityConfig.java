
package com.shop.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http

            .authorizeHttpRequests(auth -> auth

                // Public GET APIs
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/packages",
                    "/api/home-photos"
                ).permitAll()

                // Public pages and resources
                .requestMatchers(
                    "/",
                    "/work",
                    "/contact",
                    "/select",
                    "/packages-page",
                    "/login",
                    "/register",
                    "/client/login",
                    "/gallery/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/uploads/home/**",
                    "/error",
                    "/webjars/**"
                ).permitAll()

                // Owner approval links from Gmail (protected by a secret one-time token)
                .requestMatchers("/owner/approval/**").permitAll()

                // Owner profile - Login required
                .requestMatchers("/owner/profile/**").authenticated()

                // All remaining pages require login
                .anyRequest().authenticated()
            )

            // Login Configuration
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/clients", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )

            // Logout Configuration
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )

            // Preserving your existing CSRF setting
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {

        return config.getAuthenticationManager();
    }
}
