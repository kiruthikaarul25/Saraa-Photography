package com.shop.service;

import com.shop.model.*;
import com.shop.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PhotoService {
    @Autowired
    private PhotoRepository photoRepository;
    @Autowired
    private ClientRepository clientRepository;

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    /** Makes every saved file name unique even when many uploads run in parallel. */
    private static final AtomicLong SEQ = new AtomicLong();

    public Client getClientRef(int clientId) {
        return clientRepository.findById(clientId)
            .orElseThrow(() -> new RuntimeException("Client not found"));
    }

    /** Same path string the gallery uses, so the preview cache key always matches. */
    public String fullPath(Photo photo) {
        return System.getProperty("user.dir") + File.separator + photo.getFilePath();
    }

    /**
     * Saves one photo. Returns null when the same file (same name + size) is
     * already in this album (e.g. a retried batch).
     */
    public Photo uploadPhoto(MultipartFile file,
            Album album, Owner owner) throws IOException {
        Path uploadPath = Paths.get(System.getProperty("user.dir"), uploadDir)
                               .toAbsolutePath();
        Files.createDirectories(uploadPath);

        String rawName = file.getOriginalFilename() == null ? "photo.jpg" : file.getOriginalFilename();
        String original = rawName.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (photoRepository.existsByAlbumIdAndFilenameEndingWithAndFileSizeAndIsDeletedFalse(
                album.getId(), "_" + original, file.getSize())) {
            return null;
        }

        String filename = System.currentTimeMillis() + "_" + SEQ.incrementAndGet() + "_" + original;
        Path filePath = uploadPath.resolve(filename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        Photo photo = new Photo();
        photo.setAlbum(album);
        photo.setOwner(owner);
        photo.setFilename(filename);
        photo.setFilePath(uploadDir + "/" + filename);  // uploads/photos/filename.jpg
        photo.setFileSize(file.getSize());
        photo.setIsDeleted(false);
        return photoRepository.save(photo);
    }

    public List<Photo> getPhotosByAlbum(int albumId) {
        return photoRepository.findByAlbumIdAndIsDeletedFalse(albumId);
    }

    public List<Photo> findAllByClientId(int clientId) {
        return photoRepository.findByAlbum_Client_IdAndIsDeletedFalse(clientId);
    }

    public long countByOwner(int ownerId) {
        return photoRepository.countByOwnerIdAndIsDeletedFalse(ownerId);
    }

    public void deletePhoto(int photoId) {
        photoRepository.findById(photoId).ifPresent(p -> {
            p.setIsDeleted(true);
            photoRepository.save(p);
        });
    }

    public Optional<Photo> getById(int id) {
        return photoRepository.findById(id);
    }

    public void restorePhoto(int photoId) {
        photoRepository.findById(photoId).ifPresent(p -> {
            p.setIsDeleted(false);
            photoRepository.save(p);
        });
    }
}