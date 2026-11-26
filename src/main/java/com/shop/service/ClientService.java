package com.shop.service;

import com.shop.model.Album;
import com.shop.model.Client;
import com.shop.repository.AlbumRepository;
import com.shop.repository.ClientRepository;
import com.shop.repository.PhotoRepository;
import com.shop.repository.PhotoSelectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
public class ClientService {

    @Autowired
    private ClientRepository clientRepository;

    // @Lazy = circular dependency error vara koodadhu nu
    @Autowired @Lazy private AlbumService albumService;
    @Autowired @Lazy private PhotoSelectionService photoSelectionService;

    @Autowired private AlbumRepository albumRepository;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private PhotoSelectionRepository photoSelectionRepository;

    // ─── Create Client ──────────────────────────────
    public Client createClient(Client client) {
        // accessToken = username (owner sets it)
        // accessPassword = password (owner sets it)
        if (client.getAccessToken() == null || client.getAccessToken().isEmpty()) {
            client.setAccessToken(
                java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        client.setStatus("pending");
        return clientRepository.save(client);
    }

    // ─── Find by Token (username) ───────────────────
    public Optional<Client> findByToken(String token) {
        return clientRepository.findByAccessToken(token);
    }

    // ─── Get by ID ──────────────────────────────────
    public Optional<Client> getById(int id) {
        return clientRepository.findById(id);
    }

    // ─── Owner-ன் Clients ───────────────────────────
    public List<Client> getClientsByOwner(int ownerId) {
        return clientRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public List<Client> findByOwner(int ownerId) {
        return clientRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    // ─── Update ─────────────────────────────────────
    public Client update(Client client) {
        return clientRepository.save(client);
    }

    // ─── Update Status ──────────────────────────────
    public void updateStatus(int clientId, String status) {
        clientRepository.findById(clientId).ifPresent(c -> {
            c.setStatus(status);
            clientRepository.save(c);
        });
    }

    // ─── Delete (selections → photos → albums → client) ─────
    @Transactional
    public void deleteById(int id) {
        // 1. selections
        photoSelectionRepository.deleteAll(photoSelectionService.getSelectionsByClient(id));
        photoSelectionRepository.flush();

        // 2. photos (soft-deleted-um serthu, direct SQL)
        List<Album> albums = albumService.getAlbumsByClient(id);
        List<Integer> albumIds = albums.stream().map(Album::getId).toList();

        if (!albumIds.isEmpty()) {
            photoRepository.deleteByAlbumIds(albumIds);

            // 3. albums
            albumRepository.deleteAllById(albumIds);
            albumRepository.flush();
        }

        // 4. client
        clientRepository.deleteById(id);
    }

    // ─── Count ──────────────────────────────────────
    public long countByOwner(int ownerId) {
        return clientRepository.countByOwnerId(ownerId);
    }
}