package com.pettracker.features.vaccinations.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.pettracker.features.pets.domain.Pet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "vaccination_records")
public class VaccinationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @NotBlank
    @Size(max = 120)
    @Column(name = "vaccine_name", nullable = false, length = 120)
    private String vaccineName;

    @NotNull
    @Column(name = "administered_on", nullable = false)
    private LocalDate administeredOn;

    @Column(name = "next_due_on")
    private LocalDate nextDueOn;

    @Size(max = 160)
    @Column(length = 160)
    private String provider;

    @Size(max = 2000)
    @Column(length = 2000)
    private String notes;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VaccinationRecord() {
    }

    public VaccinationRecord(Pet pet, String vaccineName, LocalDate administeredOn, LocalDate nextDueOn,
            String provider, String notes) {
        this.pet = pet;
        this.vaccineName = vaccineName;
        this.administeredOn = administeredOn;
        this.nextDueOn = nextDueOn;
        this.provider = provider;
        this.notes = notes;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void update(String vaccineName, LocalDate administeredOn, LocalDate nextDueOn,
            String provider, String notes) {
        this.vaccineName = vaccineName;
        this.administeredOn = administeredOn;
        this.nextDueOn = nextDueOn;
        this.provider = provider;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public String getVaccineName() {
        return vaccineName;
    }

    public LocalDate getAdministeredOn() {
        return administeredOn;
    }

    public LocalDate getNextDueOn() {
        return nextDueOn;
    }

    public String getProvider() {
        return provider;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
