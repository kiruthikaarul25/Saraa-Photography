package com.shop.controller;

import com.shop.model.*;
import com.shop.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
public class PhotoController {

    @Autowired private PhotoService photoService;
    @Autowired private AlbumService albumService;
    @Autowired private OwnerService ownerService;
    @Autowired private ClientService clientService;
    @Autowired private OrderService orderService;
    @Autowired private PhotoSelectionService photoSelectionService;
    @Autowired private WatermarkService watermarkService;

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    /** Parallel upload batches must not create the same album twice. */
    private static final Object ALBUM_LOCK = new Object();

    // NOTE: /gallery/photo/{photoId} (client watermarked photo) lives in GalleryController.

    // ─── Owner thumbnail (view-client page) ───────
    // Small 700px preview, so the owner page does not load 8 MB originals.
    @GetMapping("/owner/photo/{photoId}/thumb")
    public ResponseEntity<Resource> ownerThumb(
            @PathVariable("photoId") int photoId) throws IOException {
        Photo photo = photoService.getById(photoId).orElseThrow();
        Path thumb = watermarkService.getPreviewPath(photoService.fullPath(photo), true);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.IMAGE_JPEG)
            .body(new FileSystemResource(thumb));
    }

    // ─── Upload Page ───────────────────────────────
    @GetMapping("/photos/upload/{clientId}")
    public String uploadPage(
            @PathVariable("clientId") int clientId,
            Authentication auth, Model model) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        Client client = clientService.getById(clientId).orElseThrow();
        model.addAttribute("client", client);
        model.addAttribute("clientId", clientId);
        model.addAttribute("albums", albumService.getAlbumsByClient(clientId));
        return "owner/upload";
    }

    // ─── Upload Submit (called many times: 15 photos per request) ───
    @PostMapping("/owner/upload")
    public String uploadPhotos(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("clientId") int clientId,
            @RequestParam(value = "albumId", required = false) Integer albumId,
            @RequestParam(value = "albumTitle", required = false) String albumTitle,
            Authentication auth) throws IOException {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        Client client = clientService.getById(clientId).orElseThrow();

        Album saved;
        if (albumId != null) {
            saved = albumService.getById(albumId).orElseThrow();
        } else {
            String title = (albumTitle != null && !albumTitle.isBlank()) ? albumTitle.trim() : "Untitled";
            // lock: 3-4 batches arrive at the same time, only ONE may create the album
            synchronized (ALBUM_LOCK) {
                saved = albumService.getAlbumsByClient(clientId).stream()
                    .filter(a -> a.getTitle().equalsIgnoreCase(title))
                    .findFirst()
                    .orElseGet(() -> {
                        Album album = new Album();
                        album.setTitle(title);
                        album.setOwner(owner);
                        album.setClient(client);
                        return albumService.createAlbum(album);
                    });
            }
        }

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                Photo photo = photoService.uploadPhoto(file, saved, owner);
                if (photo != null) {
                    // thumbnail + HD preview are built in the background
                    watermarkService.pregenerate(photoService.fullPath(photo));
                }
            }
        }
        return "redirect:/clients/" + clientId;
    }

    // ─── Download Selected Photos as ZIP ──────────
    @GetMapping("/owner/clients/{clientId}/download/selected")
    public ResponseEntity<StreamingResponseBody> downloadSelected(
            @PathVariable("clientId") int clientId) {

        Set<Integer> selectedIds = photoSelectionService
            .getSelectionsByClient(clientId).stream()
            .map(s -> s.getPhoto().getId())
            .collect(Collectors.toSet());

        List<Photo> selected = photoService.findAllByClientId(clientId).stream()
            .filter(p -> selectedIds.contains(p.getId()))
            .collect(Collectors.toList());

        Client client = clientService.getById(clientId).orElseThrow();
        String zipName = client.getCoupleName().replaceAll("[^a-zA-Z0-9]", "_") + "_Selected.zip";
        return zipResponse(selected, zipName);
    }

    // ─── Download Unselected Photos as ZIP ────────
    @GetMapping("/owner/clients/{clientId}/download/unselected")
    public ResponseEntity<StreamingResponseBody> downloadUnselected(
            @PathVariable("clientId") int clientId) {

        Set<Integer> selectedIds = photoSelectionService
            .getSelectionsByClient(clientId).stream()
            .map(s -> s.getPhoto().getId())
            .collect(Collectors.toSet());

        List<Photo> unselected = photoService.findAllByClientId(clientId).stream()
            .filter(p -> !selectedIds.contains(p.getId()))
            .collect(Collectors.toList());

        Client client = clientService.getById(clientId).orElseThrow();
        String zipName = client.getCoupleName().replaceAll("[^a-zA-Z0-9]", "_") + "_Unselected.zip";
        return zipResponse(unselected, zipName);
    }

    // ─── ZIP Helper: STREAMS to the browser (5,000 photos will not fill memory) ───
    private ResponseEntity<StreamingResponseBody> zipResponse(List<Photo> photos, String zipName) {
        StreamingResponseBody body = out -> {
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out, 1 << 16))) {
                zos.setLevel(0); // JPEGs are already compressed, no need to waste CPU
                for (Photo photo : photos) {
                    Path filePath = Paths.get(System.getProperty("user.dir"), photo.getFilePath());
                    if (Files.exists(filePath)) {
                        zos.putNextEntry(new ZipEntry(photo.getFilename()));
                        Files.copy(filePath, zos);
                        zos.closeEntry();
                    }
                }
            }
        };
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);
    }

    // ─── Delete Photo ──────────────────────────────
    @PostMapping("/owner/photos/{photoId}/delete")
    public String deletePhoto(
            @PathVariable("photoId") int photoId,
            @RequestParam(value = "clientId", required = false) Integer clientId) {
        photoService.deletePhoto(photoId);
        if (clientId != null) {
            return "redirect:/clients/" + clientId;
        }
        return "redirect:/clients";
    }

    // ─── Create Folder Page ────────────────────────
    @GetMapping("/owner/folders/new")
    public String newFolderPage(Authentication auth, Model model) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        model.addAttribute("clients", clientService.findByOwner(owner.getId()));
        return "owner/create-folder";
    }

    // ─── Create Folder Submit ──────────────────────
    @PostMapping("/owner/folders/new")
    public String createFolder(
            @RequestParam("name") String name,
            @RequestParam("clientId") int clientId,
            Authentication auth) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        Client client = clientService.getById(clientId).orElseThrow();
        Album album = new Album();
        album.setTitle(name);
        album.setOwner(owner);
        album.setClient(client);
        albumService.createAlbum(album);
        return "redirect:/photos/upload/" + clientId;
    }

    // ─── Orders Page ───────────────────────────────
    @GetMapping("/owner/orders")
    public String ordersPage(Authentication auth, Model model) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        model.addAttribute("orders", orderService.findByOwner(owner.getId()));
        return "owner/orders";
    }
}