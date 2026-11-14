package com.shop.model;

import java.util.ArrayList;
import java.util.List;

/** One slide = all photos that were uploaded together. Not a database table. */
public class PhotoGroup {
    private final String key;
    private final List<HomePhoto> photos = new ArrayList<>();

    public PhotoGroup(String key) { this.key = key; }

    public String getKey() { return key; }
    public List<HomePhoto> getPhotos() { return photos; }
    public int getCount() { return photos.size(); }
    public HomePhoto getFirst() { return photos.get(0); }
    public String getTitle() { return getFirst().getTitle(); }
    public String getCategory() { return getFirst().getCategory(); }
    public String getCategoryLabel() { return getFirst().getCategoryLabel(); }

    /** Position of this slide on the page (lower = earlier). */
    public int getSlideOrder() {
        int min = Integer.MAX_VALUE;
        for (HomePhoto p : photos) min = Math.min(min, p.getSlideOrder());
        return min == Integer.MAX_VALUE ? 0 : min;
    }
}
