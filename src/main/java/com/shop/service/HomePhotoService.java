package com.shop.service;

import com.shop.model.HomeCategory;
import com.shop.model.HomePhoto;
import com.shop.model.PhotoGroup;
import com.shop.repository.HomeCategoryRepository;
import com.shop.repository.HomePhotoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class HomePhotoService {

    /** Must stay under "uploads/" because WebConfig serves /uploads/** from file:uploads/ */
    private static final String DIR = "uploads/home";

    @Autowired private HomePhotoRepository repo;
    @Autowired private HomeCategoryRepository categoryRepo;

    private Path dir() throws IOException {
        Path p = Paths.get(System.getProperty("user.dir"), DIR).toAbsolutePath();
        Files.createDirectories(p);
        return p;
    }

    private String store(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "photo.jpg" : file.getOriginalFilename();
        String filename = System.currentTimeMillis() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
        Files.copy(file.getInputStream(), dir().resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    // ───────────── Topics (categories) ─────────────

    /**
     * All topics. Self-healing: if the table is empty the 4 defaults are created, and any topic code that
     * existing photos already use but is missing in the table is added, so no photo is ever hidden.
     */
    @Transactional
    public List<HomeCategory> categories() {
        List<HomeCategory> list = categoryRepo.findAllByOrderByIdAsc();
        if (list.isEmpty()) {
            categoryRepo.save(new HomeCategory("PRE_WEDDING", "Pre-Wedding"));
            categoryRepo.save(new HomeCategory("WEDDING", "Wedding"));
            categoryRepo.save(new HomeCategory("PUBERTY", "Puberty"));
            categoryRepo.save(new HomeCategory("VALAIKAPPU", "Valaikappu"));
            list = categoryRepo.findAllByOrderByIdAsc();
        }
        Set<String> known = new HashSet<>();
        for (HomeCategory c : list) known.add(c.getCode());
        boolean added = false;
        for (HomePhoto p : repo.findAll()) {
            String code = p.getCategory();
            if (known.add(code)) {
                categoryRepo.save(new HomeCategory(code, p.getCategoryLabel()));
                added = true;
            }
        }
        return added ? categoryRepo.findAllByOrderByIdAsc() : list;
    }

    private void applyLabels(Collection<HomePhoto> photos) {
        Map<String, String> labels = new HashMap<>();
        for (HomeCategory c : categoryRepo.findAll()) labels.put(c.getCode(), c.getLabel());
        for (HomePhoto p : photos) {
            String l = labels.get(p.getCategory());
            if (l != null) p.setCategoryLabel(l);
        }
    }

    @Transactional
    public HomeCategory addCategory(String label) {
        String clean = label == null ? "" : label.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("Topic name empty");
        if (clean.length() > 60) clean = clean.substring(0, 60);

        Optional<HomeCategory> same = categoryRepo.findByLabelIgnoreCase(clean);
        if (same.isPresent()) return same.get();

        String code = clean.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) code = "T" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        if (code.length() > 28) code = code.substring(0, 28);
        final String finalCode = code, finalLabel = clean;
        return categoryRepo.findByCode(finalCode)
                .orElseGet(() -> categoryRepo.save(new HomeCategory(finalCode, finalLabel)));
    }

    /** Returns false when slides still use this topic (move/delete them first). */
    @Transactional
    public boolean deleteCategory(long id) {
        Optional<HomeCategory> c = categoryRepo.findById(id);
        if (c.isEmpty()) return true;
        for (HomePhoto p : repo.findAll())
            if (p.getCategory().equals(c.get().getCode())) return false;
        categoryRepo.delete(c.get());
        return true;
    }

    // ───────────── Reading ─────────────

    public List<HomePhoto> findAll() {
        List<HomePhoto> all = repo.findAllByOrderByIdDesc();
        applyLabels(all);
        return all;
    }

    /** All photos of one category, newest first. Old rows with no category count as WEDDING. */
    public List<HomePhoto> findByCategory(String category) {
        List<HomePhoto> out = new ArrayList<>();
        for (HomePhoto p : findAll())
            if (p.getCategory().equals(category)) out.add(p);
        return out;
    }

    /**
     * Slides of one category (or all when category == null).
     * Slides are ordered by slideOrder (admin drag), then newest first; photos by sortOrder, then upload order.
     */
    public List<PhotoGroup> findGroups(String category) {
        Map<String, PhotoGroup> map = new LinkedHashMap<>();
        for (HomePhoto p : findAll()) {
            if (category != null && !p.getCategory().equals(category)) continue;
            map.computeIfAbsent(p.getGroupKey(), PhotoGroup::new).getPhotos().add(p);
        }
        for (PhotoGroup g : map.values())
            g.getPhotos().sort(Comparator.comparingInt(HomePhoto::getSortOrder).thenComparingInt(HomePhoto::getId));
        List<PhotoGroup> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparingInt(PhotoGroup::getSlideOrder));   // stable: ties stay newest first
        return list;
    }

    // ───────────── Writing ─────────────

    /** All photos chosen in one upload go into ONE slide, however many there are. */
    @Transactional
    public int addGroup(String title, String category, List<MultipartFile> files, String existingKey) throws IOException {
        boolean isNew = existingKey == null || existingKey.isBlank();
        String key = isNew ? UUID.randomUUID().toString() : existingKey;

        int slideOrder = 0, nextSort = 0;
        List<HomePhoto> all = repo.findAll();
        if (isNew) {
            int min = 0;
            for (HomePhoto p : all) min = Math.min(min, p.getSlideOrder());
            slideOrder = min - 1;                       // new slide appears first
        } else {
            boolean found = false;
            for (HomePhoto p : all) {
                if (!p.getGroupKey().equals(key)) continue;
                if (!found) { slideOrder = p.getSlideOrder(); found = true; }
                nextSort = Math.max(nextSort, p.getSortOrder() + 1);
            }
        }

        int n = 0;
        for (MultipartFile f : files) {
            if (f.isEmpty()) continue;
            HomePhoto hp = new HomePhoto();
            hp.setTitle(title == null || title.isBlank() ? "Our Work" : title.trim());
            hp.setCategory(category);
            hp.setGroupKey(key);
            hp.setSlideOrder(slideOrder);
            hp.setSortOrder(nextSort + n);
            hp.setFilename(store(f));
            repo.save(hp);
            n++;
        }
        return n;
    }

    /** Change title / category of the whole slide. */
    @Transactional
    public void updateGroup(String key, String title, String category) {
        for (HomePhoto p : repo.findAll()) {
            if (!p.getGroupKey().equals(key)) continue;
            if (title != null && !title.isBlank()) p.setTitle(title.trim());
            if (category != null && !category.isBlank()) p.setCategory(category);
            repo.save(p);
        }
    }

    @Transactional
    public void deleteGroup(String key) {
        for (HomePhoto p : new ArrayList<>(repo.findAll()))
            if (p.getGroupKey().equals(key)) delete(p.getId());
    }

    /**
     * Admin drag & drop: the photos in {@code ids} (in this order) now belong to slide {@code groupKey}.
     * A photo dragged from another slide takes the title / topic / position of this slide.
     */
    @Transactional
    public void reorderPhotos(String groupKey, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        HomePhoto ref = null;
        for (HomePhoto p : repo.findAll())
            if (p.getGroupKey().equals(groupKey)) { ref = p; break; }
        if (ref == null) ref = repo.findById(ids.get(0)).orElse(null);

        String title = ref == null ? null : ref.getTitle();
        String category = ref == null ? null : ref.getCategory();
        int slideOrder = ref == null ? 0 : ref.getSlideOrder();

        for (int i = 0; i < ids.size(); i++) {
            final int sort = i;
            repo.findById(ids.get(i)).ifPresent(p -> {
                p.setGroupKey(groupKey);
                if (title != null) p.setTitle(title);
                if (category != null) p.setCategory(category);
                p.setSlideOrder(slideOrder);
                p.setSortOrder(sort);
                repo.save(p);
            });
        }
    }

    /** Admin drag & drop of whole slides: keys[0] is shown first. */
    @Transactional
    public void reorderSlides(List<String> keys) {
        if (keys == null) return;
        for (HomePhoto p : repo.findAll()) {
            int idx = keys.indexOf(p.getGroupKey());
            if (idx >= 0) { p.setSlideOrder(idx); repo.save(p); }
        }
    }

    public Optional<HomePhoto> getById(int id) { return repo.findById(id); }

    /** Each file is saved as its own record with its own title. */
    @Transactional
    public HomePhoto add(String title, String category, MultipartFile file) throws IOException {
        HomePhoto hp = new HomePhoto();
        hp.setTitle(title == null || title.isBlank() ? "Untitled" : title.trim());
        hp.setCategory(category);
        hp.setFilename(store(file));
        return repo.save(hp);
    }

    /** Edit title and (optionally) replace the image. */
    @Transactional
    public Optional<HomePhoto> update(int id, String title, String category, MultipartFile newFile) throws IOException {
        return repo.findById(id).map(hp -> {
            try {
                if (title != null && !title.isBlank()) hp.setTitle(title.trim());
                if (category != null && !category.isBlank()) hp.setCategory(category);
                if (newFile != null && !newFile.isEmpty()) {
                    String old = hp.getFilename();
                    hp.setFilename(store(newFile));
                    Files.deleteIfExists(dir().resolve(old));
                }
                return repo.save(hp);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Transactional
    public void delete(int id) {
        repo.findById(id).ifPresent(hp -> {
            try { Files.deleteIfExists(dir().resolve(hp.getFilename())); } catch (IOException ignored) {}
            repo.delete(hp);
        });
    }
}
