package com.shop.controller;

import com.shop.entity.ContactMessage;
import com.shop.repository.ContactMessageRepository;
import com.shop.service.HomePhotoService;
import com.shop.service.OwnerService;
import com.shop.model.Owner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class IndexController {

    @Autowired
    private HomePhotoService homePhotoService;

    @Autowired
    private OwnerService ownerService;

    @Autowired
    private ContactMessageRepository contactMessageRepository;

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
    public String select() {
        return "select";
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