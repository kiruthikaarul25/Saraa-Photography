package com.shop.repository;

import com.shop.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface PhotoRepository
        extends JpaRepository<Photo, Integer> {

    // Album photos (not deleted)
    List<Photo> findByAlbumIdAndIsDeletedFalse(
            int albumId);

    // Client-ன் all photos (not deleted)
    List<Photo> findByAlbum_Client_IdAndIsDeletedFalse(
            int clientId);

    // Owner photo count
    long countByOwnerIdAndIsDeletedFalse(int ownerId);

    // Duplicate guard: same original name + same size already in this album
    // (makes upload retries safe - a retried batch will not create duplicates)
    boolean existsByAlbumIdAndFilenameEndingWithAndFileSizeAndIsDeletedFalse(
            int albumId, String filenameSuffix, Long fileSize);

    // Client delete: albums-ku ulla ALL photos (soft-deleted-um serthu)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM photos WHERE album_id IN (:ids)", nativeQuery = true)
    void deleteByAlbumIds(@Param("ids") List<Integer> ids);
}