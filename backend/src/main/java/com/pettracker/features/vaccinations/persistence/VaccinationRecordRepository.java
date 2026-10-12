package com.pettracker.features.vaccinations.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pettracker.features.vaccinations.domain.VaccinationRecord;

public interface VaccinationRecordRepository extends JpaRepository<VaccinationRecord, UUID> {
    List<VaccinationRecord> findAllByPetIdOrderByAdministeredOnDesc(UUID petId);
}
