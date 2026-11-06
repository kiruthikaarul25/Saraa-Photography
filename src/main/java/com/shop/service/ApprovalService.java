package com.shop.service;

import com.shop.model.Owner;
import com.shop.model.PendingApproval;
import com.shop.repository.OwnerRepository;
import com.shop.repository.PendingApprovalRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class ApprovalService {

    private final PendingApprovalRepository pendingRepository;
    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;
    private final OwnerNotificationService notificationService;

    private final SecureRandom secureRandom = new SecureRandom();

    public ApprovalService(
            PendingApprovalRepository pendingRepository,
            OwnerRepository ownerRepository,
            PasswordEncoder passwordEncoder,
            OwnerNotificationService notificationService) {

        this.pendingRepository = pendingRepository;
        this.ownerRepository = ownerRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional
    public void requestSignup(String username, String rawPassword) {

        String email = username.trim().toLowerCase();

        if (ownerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "This username is already taken. Please login.");
        }

        createRequest("SIGNUP", email, username.trim(), rawPassword);
    }

    @Transactional
    public void requestPasswordChange(
            String email, String username, String rawPassword) {

        createRequest(
                "PASSWORD_CHANGE",
                email.trim().toLowerCase(),
                username,
                rawPassword
        );
    }

    private void createRequest(
            String type,
            String email,
            String username,
            String rawPassword) {

        pendingRepository.deleteByEmailAndType(email, type);

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        PendingApproval request = new PendingApproval();

        request.setType(type);
        request.setEmail(email);
        request.setDisplayName(username);
        request.setPasswordHash(passwordEncoder.encode(rawPassword));
        request.setTokenHash(hashToken(rawToken));
        request.setCreatedAt(LocalDateTime.now());
        request.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        pendingRepository.saveAndFlush(request);

        // Send approval email to Owner
        notificationService.sendApprovalRequest(
                type, username, rawToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();

            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }

            return result.toString();

        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate token hash.");
        }
    }

    private PendingApproval getValidRequest(String rawToken) {

        PendingApproval request = pendingRepository
                .findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or already used approval link."));

        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            pendingRepository.delete(request);

            throw new IllegalArgumentException(
                    "Approval link expired. Please submit a new request.");
        }

        return request;
    }

    /** Used by the review page. Throws if the link is invalid or expired. */
    public PendingApproval review(String rawToken) {
        return getValidRequest(rawToken);
    }

    @Transactional
    public String approve(String rawToken) {

        PendingApproval request = getValidRequest(rawToken);

        if ("SIGNUP".equals(request.getType())) {

            if (ownerRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException(
                        "This username already has an account.");
            }

            Owner owner = Owner.builder()
                    .name(request.getDisplayName())
                    .email(request.getEmail())
                    .passwordHash(request.getPasswordHash())
                    .studioName("Dream Gallery Shop")
                    .build();

            ownerRepository.save(owner);

        } else if ("PASSWORD_CHANGE".equals(request.getType())) {

            Owner owner = ownerRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Admin account was not found."));

            owner.setPasswordHash(request.getPasswordHash());
            ownerRepository.save(owner);

        } else {
            throw new IllegalArgumentException("Unknown request type.");
        }

        String type = request.getType();
        pendingRepository.delete(request);

        return type;
    }

    @Transactional
    public void reject(String rawToken) {

        PendingApproval request = getValidRequest(rawToken);

        pendingRepository.delete(request);
    }
}