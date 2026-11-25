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

import org.springframework.security.core.Authentication;
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

    // ====== CHANGE THESE TWO if your URLs are different ======
    /** Where an ADMIN lands after login. */
    private static final String ADMIN_HOME = "/clients";
    /** Where a CLIENT lands after login (the client's own photo page). */
    private static final String CLIENT_HOME = "/gallery/view";
    // =========================================================

    /** The select page sends this header with every fetch() call. */
    private static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));
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
                    "/select/**",
                    "/packages-page",
                    "/login",
                    "/register",
                    "/forgot",
                    "/client/login",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/uploads/home/**",
                    "/error",
                    "/webjars/**"
                ).permitAll()

                // Owner approval links from Gmail (secret one-time token)
                .requestMatchers("/owner/approval/**").permitAll()

                // Client pages (gallery.html) - client session is checked in the controller
                .requestMatchers("/gallery/**", "/client/**", "/api/client/**").permitAll()

                // OWNER (admin) panel pages (sidebar: Clients, Work Photos, Packages,
                // Contact Enquiries, Profile, Add Client)
                .requestMatchers(
                    "/clients/**",
                    "/owner/**",
                    "/admin/**"
                ).hasRole("OWNER")

                // Anything else needs login
                .anyRequest().authenticated()
            )

            // Not logged in -> Select page. Logged in but wrong role -> Select page too.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (isAjax(request)) {
                        writeJson(response, 401,
                            "{\"success\":false,\"message\":\"Please login again.\"}");
                    } else {
                        response.sendRedirect("/select#admin");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (isAjax(request)) {
                        writeJson(response, 403,
                            "{\"success\":false,\"message\":\"Access denied.\"}");
                    } else {
                        // client tried to open an admin page -> send back to own page
                        response.sendRedirect(CLIENT_HOME);
                    }
                })
            )

            // Login (form is on /select, answered with JSON for fetch())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    String target = isAdmin(authentication) ? ADMIN_HOME : CLIENT_HOME;

                    if (isAjax(request)) {
                        writeJson(response, 200,
                            "{\"success\":true,\"redirect\":\"" + target + "\"}");
                    } else {
                        response.sendRedirect(target);
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

            // Logout
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/select#admin")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )

            // Existing CSRF setting
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