package com.pettracker.features.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Service;

@Service
public class PasswordHasher {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private final SecureRandom random = new SecureRandom();
    public String hash(String password) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        return ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public boolean matches(String password, String stored) {
        try { String[] parts = stored.split("\\$", -1); byte[] actual = derive(password, Base64.getDecoder().decode(parts[1]), Integer.parseInt(parts[0])); return MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[2])); } catch (RuntimeException e) { return false; }
    }
    private byte[] derive(String password, byte[] salt, int iterations) {
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)).getEncoded(); } catch (Exception e) { throw new IllegalStateException("Password hashing unavailable", e); }
    }
}
