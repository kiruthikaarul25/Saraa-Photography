package com.shop.controller;

import com.shop.service.*;
import com.shop.model.Owner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OwnerController {

    @Autowired
    private OwnerService ownerService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Dashboard
    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/clients";
    }

    // Profile Page
    @GetMapping("/owner/profile")
    public String profilePage(Authentication auth, Model model) {

        Owner owner = ownerService.getLoggedInOwner(auth.getName());

        model.addAttribute("owner", owner);

        return "owner/profile";
    }

    // Profile Update
    @PostMapping("/owner/profile")
    public String updateProfile(
            @ModelAttribute Owner owner,
            Authentication auth) {

        Owner existing =
                ownerService.getLoggedInOwner(auth.getName());

        owner.setId(existing.getId());
        owner.setPasswordHash(existing.getPasswordHash());

        ownerService.update(owner);

        return "redirect:/owner/profile";
    }

    // Password Change Request
    @PostMapping("/owner/password-change-request")
    public String requestPasswordChange(
            Authentication auth,
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirect) {

        Owner owner =
                ownerService.getLoggedInOwner(auth.getName());

        // Verify current password
        if (!passwordEncoder.matches(
                currentPassword,
                owner.getPasswordHash())) {

            redirect.addFlashAttribute(
                    "passwordError",
                    "Current password is incorrect.");

            return "redirect:/owner/profile";
        }

        // Validate new password
        if (newPassword == null || newPassword.length() < 6) {

            redirect.addFlashAttribute(
                    "passwordError",
                    "New password must be at least 6 characters.");

            return "redirect:/owner/profile";
        }

        // Confirm password
        if (!newPassword.equals(confirmPassword)) {

            redirect.addFlashAttribute(
                    "passwordError",
                    "New passwords do not match.");

            return "redirect:/owner/profile";
        }

        try {

            approvalService.requestPasswordChange(
                    owner.getEmail(),
                    owner.getName(),
                    newPassword);

            redirect.addFlashAttribute(
                    "passwordMessage",
                    "Password change request submitted. Wait for owner approval.");

        } catch (Exception ex) {

            redirect.addFlashAttribute(
                    "passwordError",
                    ex.getMessage() == null
                            ? "Could not submit password change request."
                            : ex.getMessage());
        }

        return "redirect:/owner/profile";
    }

    // Stats API
    @GetMapping("/owner/stats")
    @ResponseBody
    public java.util.Map<String, Object> getStats(
            Authentication auth) {

        Owner owner =
                ownerService.getLoggedInOwner(auth.getName());

        java.util.Map<String, Object> stats =
                new java.util.HashMap<>();

        stats.put(
                "totalClients",
                clientService.countByOwner(owner.getId()));

        stats.put(
                "totalPhotos",
                photoService.countByOwner(owner.getId()));

        return stats;
    }
}