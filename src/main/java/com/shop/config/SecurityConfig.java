package com.shop.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    /** The select page sends this header with every fetch() call. */
    private static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private static void writeJson(HttpServletResponse response, int status, String json)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }

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
                    "/select/**",           // new-login / reset buttons of the Select page
                    "/packages-page",
                    "/login",
                    "/register",
                    "/forgot",              // admin "new password" request (needs owner approval by mail)
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

            // Not logged in -> go to the Select page, Admin side
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (isAjax(request)) {
                        writeJson(response, 401,
                            "{\"success\":false,\"message\":\"Please login again.\"}");
                    } else {
                        response.sendRedirect("/select#admin");
                    }
                })
            )

            // Login Configuration (form is on /select, answered with JSON for fetch())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    if (isAjax(request)) {
                        writeJson(response, 200,
                            "{\"success\":true,\"redirect\":\"/clients\"}");
                    } else {
                        response.sendRedirect("/clients");
                    }
                })
                .failureHandler((request, response, exception) -> {
                    if (isAjax(request)) {
                        writeJson(response, 401,
                            "{\"success\":false,\"message\":\"Invalid username or password!\"}");
                    } else {
                        response.sendRedirect("/select#admin");
                    }
                })
                .permitAll()
            )

            // Logout Configuration
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/select#admin")
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