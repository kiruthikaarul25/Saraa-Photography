package com.shop.controller;

import com.shop.model.Owner;
import com.shop.service.PackageService;
import com.shop.service.OwnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/owner/packages")
public class PackageController {

    @Autowired private PackageService packageService;
    @Autowired private OwnerService ownerService;

    // ─── List Packages ──────────────────────────────
    @GetMapping
    public String listPackages(Authentication auth, Model model) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        model.addAttribute("packages", packageService.getPackagesByOwner(owner.getId()));
        model.addAttribute("owner", owner);
        return "owner/packages";
    }

    // ─── Create Package ─────────────────────────────
    @PostMapping({"", "/create"})
    public String createPackage(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") double price,
            @RequestParam(name = "features", required = false) String features,
            @RequestParam(name = "featured", required = false) String featured,
            Authentication auth) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        com.shop.model.Package pkg = new com.shop.model.Package();
        pkg.setName(name);
        pkg.setDescription(description);
        pkg.setPrice(java.math.BigDecimal.valueOf(price));
        pkg.setFeatures(cleanLines(features));
        pkg.setFeatured("true".equals(featured));
        pkg.setOwner(owner);
        packageService.createPackage(pkg);
        return "redirect:/owner/packages";
    }

    // ─── Edit Package Page ──────────────────────────
    @GetMapping("/{id}/edit")
    public String editPackagePage(@PathVariable("id") int id, Model model) {
        packageService.getById(id).ifPresent(pkg -> model.addAttribute("pkg", pkg));
        return "owner/edit-package";
    }

    // ─── Edit Package Submit ────────────────────────
    @PostMapping("/{id}/edit")
    public String editPackage(
            @PathVariable("id") int id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") double price,
            @RequestParam(name = "features", required = false) String features,
            @RequestParam(name = "featured", required = false) String featured,
            @RequestParam(name = "isActive", required = false) String isActive) {
        packageService.getById(id).ifPresent(pkg -> {
            pkg.setName(name);
            pkg.setDescription(description);
            pkg.setPrice(java.math.BigDecimal.valueOf(price));
            pkg.setFeatures(cleanLines(features));
            pkg.setFeatured("true".equals(featured));
            pkg.setIsActive("true".equals(isActive));
            packageService.update(pkg);
        });
        return "redirect:/owner/packages";
    }

    // ─── Delete Package ─────────────────────────────
    @PostMapping("/{id}/delete")
    public String deletePackage(@PathVariable("id") int id) {
        packageService.deletePackage(id);
        return "redirect:/owner/packages";
    }

    // one feature per line, blank lines removed
    private static String cleanLines(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\R"))
            if (!line.isBlank()) sb.append(line.trim()).append("\n");
        return sb.toString().trim();
    }
}