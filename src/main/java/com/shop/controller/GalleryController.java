package com.shop.controller;

import com.shop.model.*;
import com.shop.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class GalleryController {

    @Autowired private ClientService clientService;
    @Autowired private PhotoService photoService;
    @Autowired private AlbumService albumService;
    @Autowired private PhotoSelectionService selectionService;
    @Autowired private PackageService packageService;
    @Autowired private WatermarkService watermarkService;

    // Old URL -> same Select page (client side)
    @GetMapping("/client/login")
    public String clientLoginPage() {
        return "redirect:/select#client";
    }

    // Client login from the Select page (fetch) -> JSON
    @PostMapping("/client/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clientLoginSubmit(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session) {

        Map<String, Object> res = new LinkedHashMap<>();
        String loginName = username.trim();

        // admin usernames (niki / admin) must never open a client gallery
        if (loginName.equalsIgnoreCase("niki") || loginName.equalsIgnoreCase("admin")) {
            res.put("success", false);
            res.put("message", "Invalid username or password!");
            return ResponseEntity.status(401).body(res);
        }

        Optional<Client> found = clientService.findByToken(loginName);

        if (found.isPresent()
                && found.get().getAccessPassword() != null
                && found.get().getAccessPassword().equals(password)) {
            Client client = found.get();
            session.setAttribute("clientId", client.getId());
            clientService.updateStatus(client.getId(), "active");
            res.put("success", true);
            res.put("redirect", "/gallery/view");
            return ResponseEntity.ok(res);
        }

        res.put("success", false);
        res.put("message", "Invalid username or password!");
        return ResponseEntity.status(401).body(res);
    }

    // Personal link sent to the client (WhatsApp) -> Select page with username filled in
    @GetMapping("/gallery/{token}")
    public String clientTokenLogin(@PathVariable("token") String token) {
        return clientService.findByToken(token)
            .map(client -> "redirect:/select?u="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8)
                + "#client")
            .orElse("error/invalid-link");
    }

    @GetMapping("/gallery/view")
    public String viewGallery(HttpSession session, Model model) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null) return "redirect:/select#client";

        Client client = clientService.getById(clientId).orElseThrow();
        List<Album> albums = albumService.getAlbumsByClient(clientId);

        Set<Integer> selectedIds = selectionService
            .getSelectionsByClient(clientId).stream()
            .map(s -> s.getPhoto().getId())
            .collect(Collectors.toSet());

        Map<Integer, List<Photo>> selectedPhotos = new LinkedHashMap<>();
        Map<Integer, List<Photo>> unselectedPhotos = new LinkedHashMap<>();

        for (Album album : albums) {
            List<Photo> all = photoService.getPhotosByAlbum(album.getId());
            selectedPhotos.put(album.getId(),
                all.stream().filter(p -> selectedIds.contains(p.getId())).collect(Collectors.toList()));
            unselectedPhotos.put(album.getId(),
                all.stream().filter(p -> !selectedIds.contains(p.getId())).collect(Collectors.toList()));
        }

        model.addAttribute("client", client);
        model.addAttribute("albums", albums);
        model.addAttribute("selectedPhotos", selectedPhotos);
        model.addAttribute("unselectedPhotos", unselectedPhotos);
        model.addAttribute("selectionCount", selectedIds.size());
        model.addAttribute("packages",
            packageService.getPackagesByOwner(client.getOwner().getId()));

        return "client/gallery";
    }

    // ✅ AJAX — no page reload
    @PostMapping("/gallery/select/{photoId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> selectPhoto(
            @PathVariable("photoId") int photoId,
            HttpSession session) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null)
            return ResponseEntity.status(401).build();

        if (!selectionService.isSelected(clientId, photoId)) {
            Client client = clientService.getById(clientId).orElseThrow();
            Photo photo = photoService.getById(photoId).orElseThrow();
            PhotoSelection ps = new PhotoSelection();
            ps.setClient(client);
            ps.setPhoto(photo);
            selectionService.selectPhoto(ps);
        }

        int count = selectionService.getSelectionsByClient(clientId).size();
        Map<String, Object> res = new HashMap<>();
        res.put("status", "selected");
        res.put("count", count);
        return ResponseEntity.ok(res);
    }

    // ─── Serve Watermarked Photo to Client ────────
    @GetMapping("/gallery/photo/{photoId}")
    public ResponseEntity<Resource> serveWatermarkedPhoto(
            @PathVariable("photoId") int photoId,
            @RequestParam(value = "size", required = false) String size,
            HttpSession session) throws IOException {

        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null)
            return ResponseEntity.status(401).build();

        Photo photo = photoService.getById(photoId).orElseThrow();

        String fullPath = System.getProperty("user.dir") + File.separator + photo.getFilePath();

        boolean thumb = "thumb".equals(size);
        Path preview = watermarkService.getPreviewPath(fullPath, thumb);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + photo.getFilename() + "\"")
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
            .contentType(MediaType.IMAGE_JPEG)
            .body(new FileSystemResource(preview));
    }

    // ✅ AJAX — no page reload
    @PostMapping("/gallery/deselect/{photoId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deselectPhoto(
            @PathVariable("photoId") int photoId,
            HttpSession session) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null)
            return ResponseEntity.status(401).build();

        selectionService.deselectPhoto(clientId, photoId);

        int count = selectionService.getSelectionsByClient(clientId).size();
        Map<String, Object> res = new HashMap<>();
        res.put("status", "deselected");
        res.put("count", count);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/gallery/submit")
    public String submitSelections(HttpSession session) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null) return "redirect:/select#client";

        clientService.updateStatus(clientId, "selecting");
        // PRG Pattern: redirect after POST so Back button works cleanly
        return "redirect:/gallery/submitted";
    }

    @GetMapping("/gallery/submitted")
    public String submittedPage(HttpSession session, Model model) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null) return "redirect:/select#client";

        var selections = selectionService.getSelectionsByClient(clientId);
        model.addAttribute("selections", selections);
        model.addAttribute("count", selections.size());
        model.addAttribute("client",
            clientService.getById(clientId).orElseThrow());
        return "client/submitted";
    }
}