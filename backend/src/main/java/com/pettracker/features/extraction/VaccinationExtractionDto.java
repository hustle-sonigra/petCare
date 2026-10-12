package com.pettracker.features.extraction;

import java.time.LocalDate;
import java.util.Map;

/** Suggestions only: null fields require manual entry, and every field requires review. */
public record VaccinationExtractionDto(
        String clinicName, String vaccineName, LocalDate administeredOn, LocalDate nextDueOn,
        Map<String, String> warnings, Map<String, String> evidence) {}
