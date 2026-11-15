package com.shop.controller;

import com.shop.entity.ContactMessage;
import com.shop.repository.ContactMessageRepository;
import com.shop.service.HomePhotoService;
import com.shop.service.OwnerService;
import com.shop.service.ClientService;
import com.shop.model.Owner;
import com.shop.model.Client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;

@Controller
public class IndexController {

    @Autowired
    private HomePhotoService homePhotoService;

    @Autowired
    private OwnerService ownerService;

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    @Autowired
    private ClientService clientService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("homePhotos", homePhotoService.findAll());
        return "index";
    }

    @GetMapping("/work")
    public String work(Model model) {
        java.util.List<com.shop.model.TopicSection> sections = new java.util.ArrayList<>();
        for (com.shop.model.HomeCategory c : homePhotoService.categories()) {
            java.util.List<com.shop.model.PhotoGroup> groups = homePhotoService.findGroups(c.getCode());
            if (groups.isEmpty()) continue;          // photos illatha topic public page-la kaatta vendaam
            sections.add(new com.shop.model.TopicSection(c.getCode(), c.getLabel(), groups));
        }
        model.addAttribute("sections", sections);
        return "work";
    }

    @GetMapping("/select")
    public String select(Model model, jakarta.servlet.http.HttpServletRequest request) {
        org.springframework.security.core.Authentication a =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean adminIn = a != null && a.isAuthenticated()
            && !(a instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        String adminName = adminIn ? a.getName() : "";

        jakarta.servlet.http.HttpSession hs = request.getSession(false);
        boolean clientIn = false;
        String clientName = "";
        if (hs != null && hs.getAttribute("clientId") != null) {
            Integer cid = (Integer) hs.getAttribute("clientId");
            Client c = clientService.getById(cid).orElse(null);
            if (c != null) {
                clientIn = true;
                clientName = (c.getCoupleName() != null && !c.getCoupleName().isBlank())
                        ? c.getCoupleName() : c.getAccessToken();
            }
        }
        model.addAttribute("adminIn", adminIn);
        model.addAttribute("adminName", adminName);
        model.addAttribute("clientIn", clientIn);
        model.addAttribute("clientName", clientName == null ? "" : clientName);
        return "select";
    }

    // "New login" button on the Select page: forget ONLY that side's login (admin or client)
    @PostMapping("/select/new-login")
    @ResponseBody
    public ResponseEntity<Void> newLogin(@RequestParam(name = "role") String role,
                                         jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession hs = request.getSession(false);
        if (hs != null) {
            if ("admin".equals(role)) {
                hs.removeAttribute("SPRING_SECURITY_CONTEXT");
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            } else if ("client".equals(role)) {
                hs.removeAttribute("clientId");
            }
        }
        return ResponseEntity.noContent().build();
    }

    // The whole website was closed and opened again (all tabs gone): forget every login.
    // Called by /js/saara-nav.js on the first page load of a new visit.
    @PostMapping("/select/reset")
    @ResponseBody
    public ResponseEntity<Void> resetVisit(jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession hs = request.getSession(false);
        if (hs != null) hs.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    // Contact Page - Admin Profile Details
    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("owner", latestOwner());
        model.addAttribute("categories", homePhotoService.categories());
        return "contact";
    }

    // Booking enquiry form submit
    // email + message are optional: the booking form only sends name, phone, eventType, bookingDate
    @PostMapping("/contact")
    public String submitContact(@RequestParam(name = "name") String name,
                                @RequestParam(name = "phone") String phone,
                                @RequestParam(name = "email", required = false, defaultValue = "") String email,
                                @RequestParam(name = "eventType") String eventType,
                                @RequestParam(name = "bookingDate") String bookingDate,
                                @RequestParam(name = "subject", required = false) String subject,
                                @RequestParam(name = "message", required = false, defaultValue = "") String message,
                                Model model) {
        try {
            ContactMessage m = new ContactMessage();
            m.setName(name);
            m.setPhone(phone);
            m.setEmail(email);
            m.setSubject((subject == null || subject.isBlank()) ? eventType : subject);
            m.setMessage("Event: " + eventType + "\nDate: " + bookingDate
                    + (message.isBlank() ? "" : "\n\n" + message));
            contactMessageRepository.save(m);
            model.addAttribute("successMessage", "Thank you! We will contact you soon.");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Something went wrong. Please try again.");
        }

        model.addAttribute("owner", latestOwner());
        model.addAttribute("categories", homePhotoService.categories());
        return "contact";
    }

    // show the owner whose Profile was saved most recently (falls back to the first owner)
    private Owner latestOwner() {
        java.util.List<Owner> owners = ownerService.getAll();
        return owners.stream()
                .filter(o -> o.getUpdatedAt() != null)
                .max(java.util.Comparator.comparing(Owner::getUpdatedAt))
                .orElse(owners.isEmpty() ? null : owners.get(0));
    }
}