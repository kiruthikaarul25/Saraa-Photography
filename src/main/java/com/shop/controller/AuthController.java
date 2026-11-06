package com.shop.controller;

import com.shop.service.ApprovalService;
import com.shop.service.OwnerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class AuthController {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");
    private final OwnerService ownerService;
    private final ApprovalService approvalService;

    public AuthController(OwnerService ownerService, ApprovalService approvalService) {
        this.ownerService = ownerService; this.approvalService = approvalService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(name="error", required=false) String error, Model model) {
        if (error != null) model.addAttribute("error", "Invalid username or password!");
        return "owner/login";
    }

    @GetMapping("/register") public String registerPage() { return "owner/register"; }

    @PostMapping("/register") @ResponseBody
    public ResponseEntity<Map<String,Object>> register(@RequestParam("username") String username,
            @RequestParam("password") String password, @RequestParam("confirmPassword") String confirmPassword) {
        String error = validate(username, password, confirmPassword);
        if (error != null) return reply(HttpStatus.BAD_REQUEST, false, error);
        try {
            approvalService.requestSignup(username.trim(), password);
            return reply(HttpStatus.OK, true, "Signup request sent to Owner Gmail. Your account will be created only after approval.");
        } catch (IllegalArgumentException ex) {
            return reply(HttpStatus.CONFLICT, false, ex.getMessage());
        } catch (Exception ex) {
            return reply(HttpStatus.SERVICE_UNAVAILABLE, false, ex.getMessage() == null ? "Could not send approval email." : ex.getMessage());
        }
    }

    private String validate(String username, String password, String confirmPassword) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) return "Username must be 3-30 characters (letters, numbers, . _ - only).";
        if (password == null || password.length() < 6) return "Password must be at least 6 characters.";
        if (!password.equals(confirmPassword)) return "Passwords do not match.";
        if (ownerService.emailExists(username.trim().toLowerCase())) return "This username is already taken. Please login.";
        return null;
    }

    private ResponseEntity<Map<String,Object>> reply(HttpStatus status, boolean success, String message) {
        Map<String,Object> body = new LinkedHashMap<>(); body.put("success", success); body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
