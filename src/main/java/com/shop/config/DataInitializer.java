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

        Owner owner = ownerRepository.findByEmail(adminEmail)
            .orElse(new Owner());

        owner.setName("Admin");
        owner.setEmail(adminEmail);
        owner.setPasswordHash(passwordEncoder.encode("niki"));
        owner.setStudioName("Dream Gallery Shop");
        owner.setPhone("9999999999");
        ownerRepository.save(owner);
        System.out.println("✅ Owner saved: " + adminEmail);

        if (packageRepository.count() == 0) {
            seed(owner, "Silver", 25000, "Perfect for intimate ceremonies and small gatherings.", false,
                "4 Hours Coverage\n1 Photographer\n200 Edited Photos\nOnline Gallery\nUSB Delivery");
            seed(owner, "Gold", 55000, "Full-day coverage for your complete wedding journey.", true,
                "Full Day Coverage\n2 Photographers\n500+ Edited Photos\nCinematic Highlights Reel\nAlbum (20 Pages)\nOnline Gallery");
            seed(owner, "Platinum", 95000, "Luxury coverage — every detail, every emotion, forever.", false,
                "2-Day Coverage\n3 Photographers + Drone\nUnlimited Edited Photos\nFull Cinematic Film\nPremium Album (40 Pages)\nSame-Day Edit\nPre-Wedding Shoot");
        }
    }
}