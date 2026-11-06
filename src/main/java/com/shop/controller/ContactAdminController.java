package com.shop.controller;

import com.shop.repository.ContactMessageRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class ContactAdminController {

    private final ContactMessageRepository repository;

    public ContactAdminController(ContactMessageRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/contact-enquiries")
    public String enquiries(Model model) {
        model.addAttribute(
            "contacts",
            repository.findAllByOrderByCreatedAtDesc()
        );
        model.addAttribute("totalContacts", repository.count());

        return "admin/contact-enquiries";
    }

    @PostMapping("/contact-enquiries/delete/{id}")
    public String deleteContact(@PathVariable("id") Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
        }
        return "redirect:/admin/contact-enquiries";
    }
}