package com.pettracker.features.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "owner")
public class Owner {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 320) private String email;
    @Column(name = "password_hash", nullable = false, length = 512) private String passwordHash;
    @Column(nullable = false, length = 80) private String timezone;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Owner() {}
    public Owner(String name, String email, String passwordHash, String timezone) { this.id = UUID.randomUUID(); this.name = name; this.email = email; this.passwordHash = passwordHash; this.timezone = timezone; }
    @PrePersist void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public UUID getId() { return id; } public String getName() { return name; } public String getEmail() { return email; } public String getPasswordHash() { return passwordHash; } public String getTimezone() { return timezone; }
}
