package com.shop.controller;

import com.shop.model.HomePhoto;
import com.shop.model.Owner;
import com.shop.service.HomePhotoService;
import com.shop.service.OwnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;

@Controller
public class HomePhotoController {

    @Autowired private HomePhotoService homePhotoService;
    @Autowired private OwnerService ownerService;

    // ─── Admin: list + upload page ──────────────────
    @GetMapping("/owner/home-photos")
    public String page(Authentication auth, Model model) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        model.addAttribute("owner", owner);
        model.addAttribute("photos", homePhotoService.findAll());
        return "owner/home-photos";
    }

    // ─── Admin: upload (each file saved separately with its own title) ───
    @PostMapping("/owner/home-photos/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files,
                         @RequestParam(value = "titles", required = false) List<String> titles,
                         @RequestParam(value = "categories", required = false) List<String> categories,
                         RedirectAttributes ra) throws IOException {
        int saved = 0;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            if (f.isEmpty()) continue;
            String t = (titles != null && i < titles.size()) ? titles.get(i) : "";
            String c = (categories != null && i < categories.size()) ? categories.get(i) : "WEDDING";
            homePhotoService.add(t, c, f);
            saved++;
        }
        ra.addFlashAttribute("msg", saved + " photo(s) added to the Work page");
        return "redirect:/owner/home-photos";
    }

    // ─── Admin: edit title / replace image ──────────
    @PostMapping("/owner/home-photos/{id}/edit")
    public String edit(@PathVariable("id") int id,
                       @RequestParam("title") String title,
                       @RequestParam(value = "category", required = false) String category,
                       @RequestParam(value = "file", required = false) MultipartFile file,
                       RedirectAttributes ra) throws IOException {
        homePhotoService.update(id, title, category, file);
        ra.addFlashAttribute("msg", "Changes saved");
        return "redirect:/owner/home-photos";
    }

    // ─── Admin: delete ──────────────────────────────
    @PostMapping("/owner/home-photos/{id}/delete")
    public String delete(@PathVariable("id") int id, RedirectAttributes ra) {
        homePhotoService.delete(id);
        ra.addFlashAttribute("msg", "Photo removed");
        return "redirect:/owner/home-photos";
    }

    // ─── Public JSON (optional use) ─────────────────
    @GetMapping("/api/home-photos")
    @ResponseBody
    public List<Map<String, Object>> api() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (HomePhoto p : homePhotoService.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("url", p.getUrl());
            m.put("category", p.getCategory());
            out.add(m);
        }
        return out;
    }
}
