package com.pettracker.features.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<Owner, UUID> { Optional<Owner> findByEmailIgnoreCase(String email); boolean existsByEmailIgnoreCase(String email); }
