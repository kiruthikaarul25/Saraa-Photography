package com.shop.controller;

import com.shop.model.Owner;
import com.shop.service.OtpService;
import com.shop.service.OwnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class AuthController {

    private final OwnerService ownerService;
    private final OtpService otpService;

    private static final Pattern EMAIL_RE = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    public AuthController(OwnerService ownerService, OtpService otpService) {
        this.ownerService = ownerService;
        this.otpService = otpService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(name = "error", required = false) String error,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password!");
        }
        return "owner/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "owner/register";
    }

    @GetMapping("/forgot")
    public String forgotPage() {
        return "owner/forgot";
    }

    @PostMapping("/register/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerSendOtp(
            @RequestParam("email") String email) {
        String mail = normalizeEmail(email);
        if (!validEmail(mail)) return error("Valid email address kudunga.");
        if (ownerService.emailExists(mail)) return error("This email is already registered.");

        try {
            otpService.sendOtp("reg", mail);
            return success("OTP sent. Check your inbox and spam folder.");
        } catch (Exception ex) {
            // Do not expose SMTP server details or credentials to the browser.
            System.err.println("Registration OTP email failed: " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
            return error("Email OTP send aagala. Gmail sender settings and App Password check pannunga.");
        }
    }

    @PostMapping("/register")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            @RequestParam("otp") String otp) {

        String cleanName = name == null ? "" : name.trim();
        String mail = normalizeEmail(email);

        if (cleanName.isBlank()) return error("Name enter pannunga.");
        if (!validEmail(mail)) return error("Valid email address kudunga.");
        if (password == null || password.length() < 6) {
            return error("Password must be at least 6 characters.");
        }
        if (!password.equals(confirmPassword)) {
            return error("Password and confirm password do not match.");
        }
        if (ownerService.emailExists(mail)) {
            return error("This email is already registered.");
        }
        if (!otpService.verify("reg", mail, otp)) {
            return error("OTP wrong, expired, or maximum attempts exceeded.");
        }

        try {
            Owner owner = new Owner();
            owner.setName(cleanName);
            owner.setEmail(mail);
            // OwnerService.register() hashes this raw password using BCrypt.
            owner.setPasswordHash(password);
            ownerService.register(owner);
            return success("Account created successfully.");
        } catch (Exception ex) {
            System.err.println("Owner registration failed: " + ex.getMessage());
            return error("Account create panna mudiyala. Please try again.");
        }
    }

    @PostMapping("/forgot/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forgotSendOtp(
            @RequestParam("email") String email) {
        String mail = normalizeEmail(email);
        if (!validEmail(mail)) return error("Valid email address kudunga.");

        // Avoid revealing whether an account exists.
        if (ownerService.emailExists(mail)) {
            try {
                otpService.sendOtp("reset", mail);
            } catch (Exception ex) {
                System.err.println("Password-reset OTP email failed: "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                return error("OTP send aagala. Gmail sender settings check pannunga.");
            }
        }
        return success("If the account exists, an OTP has been sent.");
    }

    @PostMapping("/forgot/reset")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reset(
            @RequestParam("email") String email,
            @RequestParam("otp") String otp,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword) {

        String mail = normalizeEmail(email);
        if (!validEmail(mail)) return error("Valid email address kudunga.");
        if (password == null || password.length() < 6) {
            return error("Password must be at least 6 characters.");
        }
        if (!password.equals(confirmPassword)) {
            return error("Password and confirm password do not match.");
        }
        if (!ownerService.emailExists(mail)) {
            return error("Invalid email or OTP.");
        }
        if (!otpService.verify("reset", mail, otp)) {
            return error("OTP wrong, expired, or maximum attempts exceeded.");
        }

        ownerService.updatePassword(mail, password);
        return success("Password changed successfully.");
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean validEmail(String email) {
        return EMAIL_RE.matcher(email).matches();
    }

    private ResponseEntity<Map<String, Object>> success(String message) {
        return ResponseEntity.ok(Map.of("ok", true, "message", message));
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "error", message));
    }
}
