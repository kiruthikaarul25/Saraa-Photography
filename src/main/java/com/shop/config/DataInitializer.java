package com.shop.config;

import com.shop.model.Owner;
import com.shop.repository.OwnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.shop.repository.PackageRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PackageRepository packageRepository;

    private void seed(Owner owner, String name, int price, String desc, boolean featured, String features) {
        com.shop.model.Package p = new com.shop.model.Package();
        p.setOwner(owner); p.setName(name);
        p.setPrice(java.math.BigDecimal.valueOf(price));
        p.setDescription(desc); p.setFeatured(featured);
        p.setFeatures(features); p.setIsActive(true);
        packageRepository.save(p);
    }

    @Override
    public void run(String... args) {
        String adminEmail = "niki@gmail.com";

        Owner owner = ownerRepository.findByEmail(adminEmail).orElse(null);

        // NO default admin is created any more (the old niki / niki login is gone).
        // If an older database still has the owner with the default password "niki",
        // lock that password so niki / niki can never log in again.
        // The owner can set a new password with "New password" on the Select page
        // (approval mail goes to the owner's Gmail).
        if (owner != null && owner.getPasswordHash() != null
                && passwordEncoder.matches("niki", owner.getPasswordHash())) {
            owner.setPasswordHash(passwordEncoder.encode(
                java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID()));
            ownerRepository.save(owner);
            System.out.println("\u26A0\uFE0F Default password of " + adminEmail
                + " was removed. Use 'New password' on the Select page to set a new one.");
        }

        // starter packages only when an owner already exists
        if (owner != null && packageRepository.count() == 0) {
            seed(owner, "Silver", 25000, "Perfect for intimate ceremonies and small gatherings.", false,
                "4 Hours Coverage\n1 Photographer\n200 Edited Photos\nOnline Gallery\nUSB Delivery");
            seed(owner, "Gold", 55000, "Full-day coverage for your complete wedding journey.", true,
                "Full Day Coverage\n2 Photographers\n500+ Edited Photos\nCinematic Highlights Reel\nAlbum (20 Pages)\nOnline Gallery");
            seed(owner, "Platinum", 95000, "Luxury coverage \u2014 every detail, every emotion, forever.", false,
                "2-Day Coverage\n3 Photographers + Drone\nUnlimited Edited Photos\nFull Cinematic Film\nPremium Album (40 Pages)\nSame-Day Edit\nPre-Wedding Shoot");
        }
    }
}