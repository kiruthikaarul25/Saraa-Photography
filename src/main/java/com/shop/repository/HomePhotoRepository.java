package com.shop.repository;

import com.shop.model.HomePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HomePhotoRepository extends JpaRepository<HomePhoto, Integer> {
    List<HomePhoto> findAllByOrderByIdDesc();
}
