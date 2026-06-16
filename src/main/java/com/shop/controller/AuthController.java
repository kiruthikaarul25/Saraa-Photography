package com.shop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String logout,
            Model model) {
        if (error != null)
            model.addAttribute("error", "Invalid email or password!");
        if (logout != null)
            model.addAttribute("msg", "Logged out successfully!");
        return "owner/login";
    }

    @GetMapping("/register")
    public String registerRedirect() {
        return "redirect:/login";
    }
}