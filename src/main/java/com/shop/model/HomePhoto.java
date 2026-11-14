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

    /** Topic code (see HomeCategory). Old rows without a value are treated as WEDDING. */
    @Column(length = 30)
    private String category = "WEDDING";

    /** Photos uploaded together share one groupKey = one slide on the Work page. */
    @Column(name = "group_key", length = 60)
    private String groupKey;

    /** Position of the photo inside its slide (lower = earlier). Integer so old NULL rows still load. */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** Position of the slide on the page (lower = earlier). Same value for all photos of a slide. */
    @Column(name = "slide_order")
    private Integer slideOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Filled by HomePhotoService from the home_categories table. */
    @Transient
    private String categoryLabel;

    public HomePhoto() {}

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getFilename() { return filename; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCategory() { return (category == null || category.isBlank()) ? "WEDDING" : category; }

    public String getCategoryLabel() {
        if (categoryLabel != null && !categoryLabel.isBlank()) return categoryLabel;
        switch (getCategory()) {
            case "PRE_WEDDING": return "Pre-Wedding";
            case "PUBERTY":     return "Puberty";
            case "VALAIKAPPU":  return "Valaikappu";
            case "WEDDING":     return "Wedding";
            default:
                String s = getCategory().replace('_', ' ').toLowerCase();
                return s.isEmpty() ? "Wedding" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
    public void setCategoryLabel(String categoryLabel) { this.categoryLabel = categoryLabel; }

    /** Old rows without a group each become their own single-photo slide. */
    public String getGroupKey() { return (groupKey == null || groupKey.isBlank()) ? "solo-" + id : groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public String getUrl() { return "/uploads/home/" + filename; }

    public int getSortOrder() { return sortOrder == null ? 0 : sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public int getSlideOrder() { return slideOrder == null ? 0 : slideOrder; }
    public void setSlideOrder(int slideOrder) { this.slideOrder = slideOrder; }

    public void setId(int id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setFilename(String filename) { this.filename = filename; }
    public void setCategory(String category) {
        this.category = (category == null || category.isBlank()) ? "WEDDING" : category.trim();
    }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
