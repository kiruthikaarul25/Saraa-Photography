package com.shop.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final long OTP_VALIDITY_MS = 5 * 60 * 1000L;
    private static final int MAX_ATTEMPTS = 5;

    private static final class Entry {
        private final String code;
        private final long expiresAt;
        private int attempts;

        private Entry(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }

        private synchronized boolean verify(String suppliedCode) {
            if (System.currentTimeMillis() > expiresAt || attempts >= MAX_ATTEMPTS) {
                return false;
            }
            attempts++;
            return code.equals(suppliedCode == null ? "" : suppliedCode.trim());
        }

        private boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }

        private synchronized boolean exhausted() {
            return attempts >= MAX_ATTEMPTS;
        }
    }

    private final Map<String, Entry> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtp(String purpose, String recipientEmail) {
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalStateException("MAIL_USERNAME environment variable is not configured.");
        }

        String normalizedEmail = recipientEmail.trim().toLowerCase();
        String key = purpose + ":" + normalizedEmail;
        String otp = String.format("%06d", random.nextInt(1_000_000));

        String subject = "Dream Gallery Shop - "
                + ("reset".equals(purpose) ? "Password Reset OTP" : "Registration OTP");
        String body = "Your Dream Gallery Shop OTP is: " + otp
                + "\n\nThis code expires in 5 minutes."
                + "\nYou can use it only once. Do not share this code with anyone.";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail.trim());
        message.setTo(normalizedEmail);
        message.setSubject(subject);
        message.setText(body);

        // Store the OTP only after Gmail accepts the outgoing email.
        mailSender.send(message);
        otpStore.put(key, new Entry(otp, System.currentTimeMillis() + OTP_VALIDITY_MS));
    }

    public boolean verify(String purpose, String email, String suppliedCode) {
        String key = purpose + ":" + email.trim().toLowerCase();
        Entry entry = otpStore.get(key);

        if (entry == null) {
            return false;
        }

        if (entry.expired() || entry.exhausted()) {
            otpStore.remove(key, entry);
            return false;
        }

        boolean matches = entry.verify(suppliedCode);
        if (matches || entry.expired() || entry.exhausted()) {
            otpStore.remove(key, entry);
        }
        return matches;
    }
}
