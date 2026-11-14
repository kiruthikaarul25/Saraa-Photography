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
        model.addAttribute("preWedding", homePhotoService.findGroups("PRE_WEDDING"));
        model.addAttribute("wedding",    homePhotoService.findGroups("WEDDING"));
        model.addAttribute("puberty",    homePhotoService.findGroups("PUBERTY"));
        model.addAttribute("valaikappu", homePhotoService.findGroups("VALAIKAPPU"));
        return "work";
    }

    @GetMapping("/packages-page")
    public String packages() {
        return "packages-public";
    }

    @GetMapping("/select")
    public String select() {
        return "select";
    }

    // Contact Page - Admin Profile Details
    @GetMapping("/contact")
    public String contact(Model model) {

        Owner owner = ownerService.getAll()
                .stream()
                .findFirst()
                .orElse(null);

        model.addAttribute("owner", owner);

        return "contact";
    }

    // Booking enquiry form submit
    @PostMapping("/contact")
    public String submitContact(@RequestParam(name = "name") String name,
                                @RequestParam(name = "phone") String phone,
                                @RequestParam(name = "email") String email,
                                @RequestParam(name = "eventType") String eventType,
                                @RequestParam(name = "bookingDate") String bookingDate,
                                @RequestParam(name = "subject", required = false) String subject,
                                @RequestParam(name = "message") String message,
                                Model model) {
        try {
            ContactMessage m = new ContactMessage();
            m.setName(name);
            m.setPhone(phone);
            m.setEmail(email);
            m.setSubject((subject == null || subject.isBlank()) ? eventType : subject);
            m.setMessage("Event: " + eventType + "\nDate: " + bookingDate + "\n\n" + message);
            contactMessageRepository.save(m);
            model.addAttribute("successMessage", "Thank you! We will contact you soon.");
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Something went wrong. Please try again.");
        }

        Owner owner = ownerService.getAll()
                .stream()
                .findFirst()
                .orElse(null);
        model.addAttribute("owner", owner);

        return "contact";
    }
}