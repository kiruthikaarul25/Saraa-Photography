package com.shop.repository;

import com.shop.model.HomeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HomeCategoryRepository extends JpaRepository<HomeCategory, Long> {
    List<HomeCategory> findAllByOrderByIdAsc();
    Optional<HomeCategory> findByCode(String code);
    Optional<HomeCategory> findByLabelIgnoreCase(String label);
}
