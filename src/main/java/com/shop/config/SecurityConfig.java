package com.shop.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private LoginAttemptService loginAttempts;

    private static final String ADMIN_HOME = "/clients";
    private static final String CLIENT_HOME = "/gallery/view";

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

    private static String attemptKey(HttpServletRequest request) {
        return LoginAttemptService.key(request.getRemoteAddr(), request.getParameter("username"));
    }

    /** Blocks POST /login after too many failures, before authentication runs. */
    private OncePerRequestFilter loginThrottleFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                if ("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath())
                        && loginAttempts.isBlocked(attemptKey(request))) {
                    if (isAjax(request)) {
                        writeJson(response, 429,
                            "{\"success\":false,\"message\":\"Too many attempts. Try again in 15 minutes.\"}");
                    } else {
                        response.sendRedirect("/select#admin");
                    }
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/packages", "/api/home-photos").permitAll()
                .requestMatchers(
                    "/", "/work", "/contact", "/select", "/select/**", "/packages-page",
                    "/login", "/register", "/forgot", "/client/login",
                    "/css/**", "/js/**", "/images/**", "/uploads/home/**",
                    "/error", "/webjars/**"
                ).permitAll()
                .requestMatchers("/owner/approval/**").permitAll()
                .requestMatchers("/gallery/**", "/client/**", "/api/client/**").permitAll()
                .requestMatchers("/clients/**", "/owner/**", "/admin/**").hasRole("OWNER")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (isAjax(request)) {
                        writeJson(response, 401, "{\"success\":false,\"message\":\"Please login again.\"}");
                    } else {
                        response.sendRedirect("/select#admin");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (isAjax(request)) {
                        writeJson(response, 403, "{\"success\":false,\"message\":\"Access denied.\"}");
                    } else {
                        response.sendRedirect(CLIENT_HOME);
                    }
                })
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    loginAttempts.recordSuccess(attemptKey(request));
                    String target = isAdmin(authentication) ? ADMIN_HOME : CLIENT_HOME;
                    if (isAjax(request)) {
                        writeJson(response, 200,
                            "{\"success\":true,\"redirect\":\"" + target + "\"}");
                    } else {
                        response.sendRedirect(target);
                    }
                })
                .failureHandler((request, response, exception) -> {
                    loginAttempts.recordFailure(attemptKey(request));
                    if (isAjax(request)) {
                        writeJson(response, 401,
                            "{\"success\":false,\"message\":\"Invalid username or password!\"}");
                    } else {
                        response.sendRedirect("/select#admin");
                    }
                })
                .permitAll()
            )
            // GET logout kept so existing "Logout" links keep working
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/select#admin")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            // CSRF ON for the owner panel. Token is in the XSRF-TOKEN cookie; Thymeleaf
            // th:action forms get the hidden field automatically; fetch() must send X-XSRF-TOKEN.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/login", "/client/login", "/gallery/**", "/owner/approval/**")
            )
            .addFilterBefore(loginThrottleFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}