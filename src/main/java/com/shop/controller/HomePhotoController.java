package com.shop.controller;

import com.shop.model.HomeCategory;
import com.shop.model.HomePhoto;
import com.shop.model.Owner;
import com.shop.service.HomePhotoService;
import com.shop.service.OwnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartException;
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
        model.addAttribute("categories", homePhotoService.categories());
        model.addAttribute("groups", homePhotoService.findGroups(null));
        return "owner/home-photos";
    }

    // ─── Admin: upload — everything chosen at once becomes ONE slide ───
    @PostMapping("/owner/home-photos/upload")
    public String upload(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                         @RequestParam(value = "title", required = false) String title,
                         @RequestParam(value = "category", required = false, defaultValue = "WEDDING") String category,
                         RedirectAttributes ra) {
        int received = files == null ? 0 : files.size();
        System.out.println("[HOME-PHOTO UPLOAD] files received = " + received + ", category = " + category + ", title = " + title);
        try {
            int saved = (files == null) ? 0 : homePhotoService.addGroup(title, category, files, null);
            System.out.println("[HOME-PHOTO UPLOAD] saved = " + saved);
            ra.addFlashAttribute("msg", saved == 0
                    ? "No photo saved (browser sent " + received + " file(s)). Please choose photos again."
                    : saved + " photo(s) saved as 1 slide in " + category);
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            ra.addFlashAttribute("msg", "UPLOAD ERROR: " + root.getClass().getSimpleName() + " - " + root.getMessage());
        }
        return "redirect:/owner/home-photos";
    }

    // Show the real reason instead of a blank error page when an upload is rejected
    @ExceptionHandler({MultipartException.class, IOException.class})
    public String uploadFailed(Exception e, RedirectAttributes ra) {
        ra.addFlashAttribute("msg", "Upload failed: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        return "redirect:/owner/home-photos";
    }

    // ─── Admin: edit a whole slide (title / category / add more photos) ───
    @PostMapping("/owner/home-photos/group/{key}/edit")
    public String editGroup(@PathVariable("key") String key,
                            @RequestParam(value = "title", required = false) String title,
                            @RequestParam("category") String category,
                            @RequestParam(value = "files", required = false) List<MultipartFile> files,
                            RedirectAttributes ra) throws IOException {
        homePhotoService.updateGroup(key, title, category);
        if (files != null) homePhotoService.addGroup(title, category, files, key);
        ra.addFlashAttribute("msg", "Slide saved");
        return "redirect:/owner/home-photos";
    }

    @PostMapping("/owner/home-photos/group/{key}/delete")
    public String deleteGroup(@PathVariable("key") String key, RedirectAttributes ra) {
        homePhotoService.deleteGroup(key);
        ra.addFlashAttribute("msg", "Slide removed");
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

    // ─── Admin (AJAX): delete one photo without reloading the page ───
    @PostMapping("/owner/home-photos/{id}/delete-ajax")
    @ResponseBody
    public ResponseEntity<Void> deleteAjax(@PathVariable("id") int id) {
        homePhotoService.delete(id);
        return ResponseEntity.ok().build();
    }

    // ─── Admin (AJAX): replace one photo's image ───
    @PostMapping("/owner/home-photos/{id}/replace")
    @ResponseBody
    public ResponseEntity<Map<String, String>> replace(@PathVariable("id") int id,
                                                       @RequestParam("file") MultipartFile file) throws IOException {
        Optional<HomePhoto> p = homePhotoService.update(id, null, null, file);
        if (p.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("url", p.get().getUrl()));
    }

    // ─── Admin (AJAX): drag & drop ───
    public record ReorderReq(String groupKey, List<Integer> ids) {}
    public record SlideOrderReq(List<String> keys) {}

    @PostMapping("/owner/home-photos/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorder(@RequestBody ReorderReq req) {
        homePhotoService.reorderPhotos(req.groupKey(), req.ids());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/owner/home-photos/slides/reorder")
    @ResponseBody
    public ResponseEntity<Void> reorderSlides(@RequestBody SlideOrderReq req) {
        homePhotoService.reorderSlides(req.keys());
        return ResponseEntity.ok().build();
    }

    // ─── Admin: topics (categories) ───
    @PostMapping("/owner/home-categories/add-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addCategoryAjax(@RequestParam("label") String label) {
        try {
            HomeCategory c = homePhotoService.addCategory(label);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("code", c.getCode());
            m.put("label", c.getLabel());
            return ResponseEntity.ok(m);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/owner/home-categories/{id}/delete-ajax")
    @ResponseBody
    public ResponseEntity<Void> deleteCategoryAjax(@PathVariable("id") long id) {
        return homePhotoService.deleteCategory(id)
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(409).build();      // slides still use this topic
    }

    // plain form fallbacks (no JavaScript)
    @PostMapping("/owner/home-categories/add")
    public String addCategory(@RequestParam("label") String label, RedirectAttributes ra) {
        try { homePhotoService.addCategory(label); ra.addFlashAttribute("msg", "Topic added"); }
        catch (IllegalArgumentException e) { ra.addFlashAttribute("msg", "Topic name empty"); }
        return "redirect:/owner/home-photos";
    }

    @PostMapping("/owner/home-categories/{id}/delete")
    public String deleteCategory(@PathVariable("id") long id, RedirectAttributes ra) {
        ra.addFlashAttribute("msg", homePhotoService.deleteCategory(id)
                ? "Topic deleted" : "Indha topic-la slides irukku, adhai vera topic-ku maathunga");
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
