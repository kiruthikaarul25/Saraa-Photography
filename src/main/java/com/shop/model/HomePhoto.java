package com.shop.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A photo (with title) shown in the "Our Work" grid of the index page. */
@Entity
@Table(name = "home_photos")
public class HomePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 255)
    private String filename;

    /** PRE_WEDDING or WEDDING (old rows without a value are treated as WEDDING). */
    @Column(length = 30)
    private String category = "WEDDING";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public HomePhoto() {}

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getFilename() { return filename; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCategory() { return (category == null || category.isBlank()) ? "WEDDING" : category; }
    public String getCategoryLabel() { return "PRE_WEDDING".equals(getCategory()) ? "Pre-Wedding" : "Wedding"; }
    public String getUrl() { return "/uploads/home/" + filename; }

    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setFilename(String filename) { this.filename = filename; }
    public void setCategory(String category) { this.category = "PRE_WEDDING".equals(category) ? "PRE_WEDDING" : "WEDDING"; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
