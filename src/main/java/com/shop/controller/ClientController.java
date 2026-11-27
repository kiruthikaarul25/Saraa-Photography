package com.shop.controller;

import com.shop.model.*;
import com.shop.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients")
public class ClientController {

    @Autowired private ClientService clientService;
    @Autowired private OwnerService ownerService;
    @Autowired private AlbumService albumService;
    @Autowired private PhotoService photoService;
    @Autowired private PhotoSelectionService photoSelectionService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8765}")
    private String baseUrl;

    private static final String PW_CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(PW_CHARS.charAt(RANDOM.nextInt(PW_CHARS.length())));
        return sb.toString();
    }

    /** Loads the client and makes sure it belongs to the logged-in owner (404 otherwise). */
    private Client requireOwned(int id, Authentication auth) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        Client client = clientService.getById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (client.getOwner() == null || !Objects.equals(client.getOwner().getId(), owner.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return client;
    }

    @GetMapping
    public String listClients(Authentication auth, Model model) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        model.addAttribute("clients", clientService.getClientsByOwner(owner.getId()));
        model.addAttribute("owner", owner);
        return "owner/clients";
    }

    @GetMapping("/add")
    public String addClientPage(Model model) {
        model.addAttribute("client", new Client());
        return "owner/add-client";
    }

    @PostMapping("/add")
    public String addClient(@ModelAttribute Client client, Authentication auth,
                            RedirectAttributes redirect) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        client.setOwner(owner);

        String raw = client.getAccessPassword();
        if (raw == null || raw.isBlank()) raw = randomPassword();
        client.setAccessPassword(passwordEncoder.encode(raw));

        Client saved = clientService.createClient(client);

        // The plain password is shown ONCE on the send-link page, never stored.
        redirect.addFlashAttribute("rawPassword", raw);
        return "redirect:/clients/" + saved.getId() + "/send-link";
    }

    @GetMapping("/{id}")
    public String viewClient(@PathVariable("id") int id, Authentication auth, Model model) {
        Client client = requireOwned(id, auth);
        model.addAttribute("client", client);

        List<Album> albums = albumService.getAlbumsByClient(id);
        model.addAttribute("albums", albums);
        model.addAttribute("photos", photoService.findAllByClientId(id));

        Set<Integer> selectedIds = photoSelectionService
            .getSelectionsByClient(id).stream()
            .map(s -> s.getPhoto().getId())
            .collect(Collectors.toSet());
        model.addAttribute("selectedIds", selectedIds);
        model.addAttribute("selectionCount", selectedIds.size());

        Map<Integer, List<Photo>> selectedPhotos = new LinkedHashMap<>();
        Map<Integer, List<Photo>> unselectedPhotos = new LinkedHashMap<>();
        for (Album album : albums) {
            List<Photo> all = photoService.getPhotosByAlbum(album.getId());
            selectedPhotos.put(album.getId(),
                all.stream().filter(p -> selectedIds.contains(p.getId())).collect(Collectors.toList()));
            unselectedPhotos.put(album.getId(),
                all.stream().filter(p -> !selectedIds.contains(p.getId())).collect(Collectors.toList()));
        }
        model.addAttribute("selectedPhotos", selectedPhotos);
        model.addAttribute("unselectedPhotos", unselectedPhotos);
        return "owner/view-client";
    }

    @GetMapping("/{id}/edit")
    public String editClientPage(@PathVariable("id") int id, Authentication auth, Model model) {
        Client c = requireOwned(id, auth);
        model.addAttribute("client", c);
        return "owner/edit-client";
    }

    @PostMapping("/{id}/edit")
    public String editClient(@PathVariable("id") int id, @ModelAttribute Client client,
                             Authentication auth) {
        Client existing = requireOwned(id, auth);
        client.setId(id);
        client.setOwner(existing.getOwner());
        client.setCreatedAt(existing.getCreatedAt());
        if (client.getAccessToken() == null || client.getAccessToken().isBlank()) {
            client.setAccessToken(existing.getAccessToken());
        }
        if (client.getAccessPassword() == null || client.getAccessPassword().isBlank()) {
            client.setAccessPassword(existing.getAccessPassword());
        } else {
            client.setAccessPassword(passwordEncoder.encode(client.getAccessPassword()));
        }
        clientService.update(client);
        return "redirect:/clients/" + id;
    }

    // GET -> POST: a link/image on another site can no longer delete a client
    @PostMapping("/{id}/delete")
    public String deleteClient(@PathVariable("id") int id, Authentication auth) {
        requireOwned(id, auth);
        clientService.deleteById(id);
        return "redirect:/clients";
    }

    /** Owner clicks "New password": generate, hash, show once on the send-link page. */
    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable("id") int id, Authentication auth,
                                RedirectAttributes redirect) {
        Client client = requireOwned(id, auth);
        String raw = randomPassword();
        client.setAccessPassword(passwordEncoder.encode(raw));
        clientService.update(client);
        redirect.addFlashAttribute("rawPassword", raw);
        return "redirect:/clients/" + id + "/send-link";
    }

    @GetMapping("/{id}/send-link")
    public String sendLinkPage(@PathVariable("id") int id, Authentication auth, Model model) {
        Client client = requireOwned(id, auth);

        String link = baseUrl + "/gallery/" + client.getAccessToken();
        model.addAttribute("link", link);
        model.addAttribute("client", client);

        // password: flash value (just created/reset) or, for old un-migrated clients, the stored plain text
        String raw = (String) model.asMap().get("rawPassword");
        String stored = client.getAccessPassword();
        if (raw == null && stored != null && !stored.startsWith("$2")) raw = stored;
        model.addAttribute("rawPassword", raw);          // null => template should show "Reset password" button
        model.addAttribute("hasPassword", raw != null);

        String whatsappMsg = "Hi " + client.getCoupleName()
            + "! Your wedding photos are ready. "
            + "View here: " + link
            + " | Username: " + client.getAccessToken()
            + (raw != null ? " | Password: " + raw : "");
        String phone = client.getPhone() == null ? "" : client.getPhone().replaceAll("[^0-9]", "");
        String whatsappUrl = "https://wa.me/" + phone + "?text="
            + java.net.URLEncoder.encode(whatsappMsg, StandardCharsets.UTF_8);
        model.addAttribute("whatsappUrl", whatsappUrl);
        return "owner/send-link";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable("id") int id, @RequestParam("status") String status,
                               Authentication auth) {
        requireOwned(id, auth);
        clientService.updateStatus(id, status);
        return "redirect:/clients/" + id;
    }
}