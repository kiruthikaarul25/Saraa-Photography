package com.shop.controller;

import com.shop.config.LoginAttemptService;
import com.shop.model.*;
import com.shop.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptService loginAttempts;

    @GetMapping("/client/login")
    public String clientLoginPage() {
        return "redirect:/select#client";
    }

    /** bcrypt hash, or (legacy) plain text that gets upgraded to bcrypt on first good login. */
    private boolean passwordMatches(Client client, String raw) {
        String stored = client.getAccessPassword();
        if (stored == null || raw == null) return false;
        if (stored.startsWith("$2")) {
            return passwordEncoder.matches(raw, stored);
        }
        boolean ok = MessageDigest.isEqual(
            stored.getBytes(StandardCharsets.UTF_8), raw.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            client.setAccessPassword(passwordEncoder.encode(raw));
            clientService.update(client);
        }
        return ok;
    }

    @PostMapping("/client/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clientLoginSubmit(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpSession session) {

        Map<String, Object> res = new LinkedHashMap<>();
        String loginName = username.trim();
        String key = LoginAttemptService.key(request.getRemoteAddr(), loginName);

        if (loginAttempts.isBlocked(key)) {
            res.put("success", false);
            res.put("message", "Too many attempts. Try again in 15 minutes.");
            return ResponseEntity.status(429).body(res);
        }

        if (loginName.equalsIgnoreCase("niki") || loginName.equalsIgnoreCase("admin")) {
            loginAttempts.recordFailure(key);
            res.put("success", false);
            res.put("message", "Invalid username or password!");
            return ResponseEntity.status(401).body(res);
        }

        Optional<Client> found = clientService.findByToken(loginName);

        if (found.isPresent() && passwordMatches(found.get(), password)) {
            Client client = found.get();
            loginAttempts.recordSuccess(key);
            request.changeSessionId(); // prevent session fixation
            session = request.getSession();
            session.setAttribute("clientId", client.getId());
            clientService.updateStatus(client.getId(), "active");
            res.put("success", true);
            res.put("redirect", "/gallery/view");
            return ResponseEntity.ok(res);
        }

        loginAttempts.recordFailure(key);
        res.put("success", false);
        res.put("message", "Invalid username or password!");
        return ResponseEntity.status(401).body(res);
    }

    @GetMapping("/gallery/{token}")
    public String clientTokenLogin(@PathVariable("token") String token) {
        return clientService.findByToken(token)
            .map(client -> "redirect:/select?u="
                + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "#client")
            .orElse("error/invalid-link");
    }

    @GetMapping("/gallery/view")
    public String viewGallery(HttpSession session, Model model) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null) return "redirect:/select#client";

        Client client = clientService.getById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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

    /** True only if the photo sits in one of THIS client's albums (and is returned by the live-photo query). */
    private Optional<Photo> findOwnedPhoto(int clientId, int photoId) {
        for (Album album : albumService.getAlbumsByClient(clientId)) {
            for (Photo p : photoService.getPhotosByAlbum(album.getId())) {
                if (p.getId() == photoId) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    @PostMapping("/gallery/select/{photoId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> selectPhoto(
            @PathVariable("photoId") int photoId,
            HttpSession session) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null)
            return ResponseEntity.status(401).build();

        Optional<Photo> photo = findOwnedPhoto(clientId, photoId);
        if (photo.isEmpty())
            return ResponseEntity.status(404).build();

        if (!selectionService.isSelected(clientId, photoId)) {
            Client client = clientService.getById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            PhotoSelection ps = new PhotoSelection();
            ps.setClient(client);
            ps.setPhoto(photo.get());
            selectionService.selectPhoto(ps);
        }

        int count = selectionService.getSelectionsByClient(clientId).size();
        Map<String, Object> res = new HashMap<>();
        res.put("status", "selected");
        res.put("count", count);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/gallery/photo/{photoId}")
    public ResponseEntity<Resource> serveWatermarkedPhoto(
            @PathVariable("photoId") int photoId,
            @RequestParam(value = "size", required = false) String size,
            HttpSession session) throws IOException {

        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null)
            return ResponseEntity.status(401).build();

        Optional<Photo> found = findOwnedPhoto(clientId, photoId);
        if (found.isEmpty())
            return ResponseEntity.status(404).build();
        Photo photo = found.get();

        String fullPath = System.getProperty("user.dir") + File.separator + photo.getFilePath();

        boolean thumb = "thumb".equals(size);
        Path preview = watermarkService.getPreviewPath(fullPath, thumb);

        String safeName = photo.getFilename() == null ? "photo.jpg"
            : photo.getFilename().replaceAll("[\"\\\\\\r\\n]", "_");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeName + "\"")
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
            .contentType(MediaType.IMAGE_JPEG)
            .body(new FileSystemResource(preview));
    }

    @PostMapping("/gallery/deselect/{photoId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deselectPhoto(
            @PathVariable("photoId") int photoId,
            HttpSession session) {
        Integer clientId = (Integer) session.getAttribute("clientId");
        if (clientId == null)
            return ResponseEntity.status(401).build();

        // deselectPhoto(clientId, photoId) is already scoped to this client's own selection row
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
            clientService.getById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "client/submitted";
    }
}