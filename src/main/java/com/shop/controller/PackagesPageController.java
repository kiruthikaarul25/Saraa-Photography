package com.shop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Handles only the public Packages page. */
@Controller
public class PackagesPageController {

    @GetMapping("/packages-page")
    public String packagesPage() {
        return "packages-public";
    }
}
