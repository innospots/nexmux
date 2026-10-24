package com.innospots.nexmux.core.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class AppKeySupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AppKeySupport() {
    }

    public record Material(String plainKey, String salt, String hash) {
    }

    public static Material generate() {
        byte[] keyBytes = new byte[32];
        RANDOM.nextBytes(keyBytes);
        String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
        return new Material(plain, salt, hash(plain, salt));
    }

    public static String hash(String plainKey, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update(plainKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
