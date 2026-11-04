package com.shop.service;

import com.shop.model.HomePhoto;
import com.shop.repository.HomePhotoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

@Service
public class HomePhotoService {

    /** Must stay under "uploads/" because WebConfig serves /uploads/** from file:uploads/ */
    private static final String DIR = "uploads/home";

    @Autowired private HomePhotoRepository repo;

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

    public List<HomePhoto> findAll() { return repo.findAllByOrderByIdDesc(); }

    /** All photos of one category, newest first. Old rows with no category count as WEDDING. */
    public List<HomePhoto> findByCategory(String category) {
        List<HomePhoto> out = new java.util.ArrayList<>();
        for (HomePhoto p : repo.findAllByOrderByIdDesc())
            if (p.getCategory().equals(category)) out.add(p);
        return out;
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
                if (category != null) hp.setCategory(category);
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
