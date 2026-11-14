package com.shop.model;

import jakarta.persistence.*;

/** A topic (category) of the Work page, e.g. Wedding, Pre-Wedding, Engagement... */
@Entity
@Table(name = "home_categories")
public class HomeCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String label;

    public HomeCategory() {}
    public HomeCategory(String code, String label) { this.code = code; this.label = label; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
