package com.shop.config;

import com.shop.model.HomeCategory;
import com.shop.repository.HomeCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Creates the 4 default topics the first time the app starts. */
@Component
public class HomeCategorySeeder implements CommandLineRunner {

    @Autowired private HomeCategoryRepository repo;

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;
        repo.save(new HomeCategory("PRE_WEDDING", "Pre-Wedding"));
        repo.save(new HomeCategory("WEDDING", "Wedding"));
        repo.save(new HomeCategory("PUBERTY", "Puberty"));
        repo.save(new HomeCategory("VALAIKAPPU", "Valaikappu"));
    }
}
