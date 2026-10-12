package com.pettracker.features.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final byte[] secret;
    public JwtService(@Value("${app.jwt.secret}") String secret) { this.secret = secret.getBytes(StandardCharsets.UTF_8); }
    public String issue(Owner owner) {
        long exp = Instant.now().plusSeconds(86_400).getEpochSecond();
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + owner.getId() + "\",\"email\":\"" + owner.getEmail() + "\",\"exp\":" + exp + "}");
        String input = header + "." + payload;
        return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(input));
    }
    public UUID verifyAndGetOwner(String token) {
        try {
            String[] parts = token.split("\\."); if (parts.length != 3 || !java.util.Arrays.equals(sign(parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(payload.replaceFirst(".*\\\"exp\\\":(\\d+).*", "$1")); if (exp < Instant.now().getEpochSecond()) return null;
            return UUID.fromString(payload.replaceFirst(".*\\\"sub\\\":\\\"([^\\\"]+).*", "$1"));
        } catch (RuntimeException e) { return null; }
    }
    private byte[] sign(String value) { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
}
