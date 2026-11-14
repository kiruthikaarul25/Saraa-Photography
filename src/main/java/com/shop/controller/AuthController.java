package com.shop.controller;

import com.shop.model.Owner;
import com.shop.service.ApprovalService;
import com.shop.service.OwnerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Admin (owner) side of the single Select page:
 *   login    -> POST /login    (Spring Security, see SecurityConfig)
 *   register -> POST /register (JSON)
 *   new pass -> POST /forgot   (JSON)
 * The old separate pages (/login, /register) now just redirect to /select.
 */
@Controller
public class AuthController {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");

    private final OwnerService ownerService;
    private final ApprovalService approvalService;

    public AuthController(OwnerService ownerService, ApprovalService approvalService) {
        this.ownerService = ownerService;
        this.approvalService = approvalService;
    }

    // ---------- old URLs -> same Select page ----------

    @GetMapping("/login")
    public String loginPage(Authentication auth) {
        // Already logged in -> go straight inside
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/clients";
        }
        return "redirect:/select#admin";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "redirect:/select#register";
    }

    // ---------- REGISTER ----------

    @PostMapping("/register")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword) {

        String error = validate(username, password, confirmPassword);
        if (error != null) return reply(HttpStatus.BAD_REQUEST, false, error);

        try {
            approvalService.requestSignup(username.trim(), password);
            return reply(HttpStatus.OK, true,
                "Signup request sent to the owner's Gmail. Your account will be created only after approval.");
        } catch (IllegalArgumentException ex) {
            return reply(HttpStatus.CONFLICT, false, ex.getMessage());
        } catch (Exception ex) {
            return reply(HttpStatus.SERVICE_UNAVAILABLE, false,
                ex.getMessage() == null ? "Could not send approval email." : ex.getMessage());
        }
    }

    // ---------- NEW PASSWORD (forgot) ----------
    // The password is NOT changed right away. An approval mail goes to the owner
    // (same flow as the profile page) and the change happens only after "Accept".

    @PostMapping("/forgot")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forgot(
            @RequestParam("username") String username,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword) {

        if (username == null || username.isBlank())
            return reply(HttpStatus.BAD_REQUEST, false, "Enter your username.");
        if (newPassword == null || newPassword.length() < 6)
            return reply(HttpStatus.BAD_REQUEST, false, "Password must be at least 6 characters.");
        if (!newPassword.equals(confirmPassword))
            return reply(HttpStatus.BAD_REQUEST, false, "Passwords do not match.");

        // Same shortcuts as CustomUserDetailsService
        String login = username.trim();
        if (login.equalsIgnoreCase("admin") || login.equalsIgnoreCase("niki")) login = "niki@gmail.com";

        // Same answer whether the user exists or not (so nobody can probe usernames)
        String okMessage = "If this username exists, a password change request was sent to the owner's Gmail. "
                + "The new password works only after approval.";

        Optional<Owner> owner = ownerService.findByEmail(login.toLowerCase());
        if (owner.isEmpty()) return reply(HttpStatus.OK, true, okMessage);

        try {
            approvalService.requestPasswordChange(owner.get().getEmail(), owner.get().getName(), newPassword);
            return reply(HttpStatus.OK, true, okMessage);
        } catch (Exception ex) {
            return reply(HttpStatus.SERVICE_UNAVAILABLE, false,
                ex.getMessage() == null ? "Could not send approval email." : ex.getMessage());
        }
    }

    // ---------- helpers ----------

    private String validate(String username, String password, String confirmPassword) {
        if (username == null || !USERNAME.matcher(username.trim()).matches())
            return "Username must be 3-30 characters (letters, numbers, . _ - only).";
        if (password == null || password.length() < 6)
            return "Password must be at least 6 characters.";
        if (!password.equals(confirmPassword))
            return "Passwords do not match.";
        if (ownerService.emailExists(username.trim().toLowerCase()))
            return "This username is already taken. Please login.";
        return null;
    }

    private ResponseEntity<Map<String, Object>> reply(HttpStatus status, boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}