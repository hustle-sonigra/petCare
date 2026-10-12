package com.pettracker.features.pets.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pettracker.features.pets.domain.Pet;

public interface PetRepository extends JpaRepository<Pet, UUID> {
    List<Pet> findAllByOwnerIdAndArchivedAtIsNullOrderByNameAsc(UUID ownerId);
    java.util.Optional<Pet> findByIdAndOwnerIdAndArchivedAtIsNull(UUID id, UUID ownerId);
}
