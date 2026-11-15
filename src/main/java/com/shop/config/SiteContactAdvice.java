package com.shop.config;

import com.shop.model.Owner;
import com.shop.service.OwnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Comparator;
import java.util.List;

/**
 * Gives every page the WhatsApp number = the same number shown on the Contact page
 * (latest owner's profile phone). Falls back to the studio default.
 */
@ControllerAdvice
public class SiteContactAdvice {

    private static final String DEFAULT_WA = "919786711698";

    @Autowired
    private OwnerService ownerService;

    @ModelAttribute("siteWhatsapp")
    public String siteWhatsapp() {
        try {
            List<Owner> owners = ownerService.getAll();
            Owner o = owners.stream()
                    .filter(x -> x.getUpdatedAt() != null)
                    .max(Comparator.comparing(Owner::getUpdatedAt))
                    .orElse(owners.isEmpty() ? null : owners.get(0));
            if (o != null && o.getPhone() != null) {
                String d = o.getPhone().replaceAll("\\D", "");
                if (d.length() == 10) d = "91" + d;      // Indian number without country code
                if (d.length() >= 11) return d;
            }
        } catch (Exception ignored) { }
        return DEFAULT_WA;
    }
}
