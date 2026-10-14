package com.pettracker.features.vaccinations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.pettracker.features.auth.AuthTokenFilter;
import com.pettracker.features.pets.PetController;
import com.pettracker.features.pets.domain.Pet;
import com.pettracker.features.pets.domain.PetSpecies;
import com.pettracker.features.vaccinations.domain.VaccinationRecord;
import com.pettracker.features.vaccinations.persistence.VaccinationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VaccinationControllerTest {
    private final VaccinationRecordRepository records = mock(VaccinationRecordRepository.class);
    private final PetController pets = mock(PetController.class);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    private VaccinationRecord record;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        Pet pet = new Pet(ownerId, "Milo", PetSpecies.DOG, null, null);
        ReflectionTestUtils.setField(pet, "id", petId);
        record = new VaccinationRecord(pet, "Rabies", LocalDate.of(2026, 10, 9), null, "Old clinic", "Old notes");
        ReflectionTestUtils.setField(record, "id", recordId);
        ReflectionTestUtils.setField(record, "createdAt", createdAt);
        when(records.findById(recordId)).thenReturn(Optional.of(record));
        when(records.save(any(VaccinationRecord.class))).thenAnswer(call -> call.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(new VaccinationController(records, pets)).build();
    }

    private String body(String name, String due) {
        return """
                {"vaccineName":"%s","administeredOn":"2026-10-10","nextDueOn":%s,
                 "provider":"New clinic","notes":"Updated notes"}
                """.formatted(name, due);
    }

    @Test
    void updatesExistingRecordAndPreservesItsIdentityPetAndCreatedDate() throws Exception {
        mvc.perform(put("/api/vaccinations/" + recordId).requestAttr(AuthTokenFilter.OWNER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON).content(body(" Booster ", "\"2027-10-10\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId.toString()))
                .andExpect(jsonPath("$.petId").value(petId.toString()))
                .andExpect(jsonPath("$.vaccineName").value("Booster"));
        verify(pets).find(petId, ownerId);
        verify(records).save(same(record));
        assertEquals(createdAt, record.getCreatedAt());
        assertEquals(LocalDate.of(2026, 10, 10), record.getAdministeredOn());
        assertEquals(LocalDate.of(2027, 10, 10), record.getNextDueOn());
        assertEquals("New clinic", record.getProvider());
        assertEquals("Updated notes", record.getNotes());
    }

    @Test
    void permitsClearingTheOptionalDueDate() throws Exception {
        record.update("Rabies", LocalDate.of(2026, 10, 9), LocalDate.of(2027, 10, 9), "Clinic", "Notes");
        mvc.perform(put("/api/vaccinations/" + recordId).requestAttr(AuthTokenFilter.OWNER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON).content(body("Booster", "null")))
                .andExpect(status().isOk());
        assertNull(record.getNextDueOn());
    }

    @Test
    void rejectsUnauthenticatedAndOtherOwnersBeforeChangingTheRecord() throws Exception {
        mvc.perform(put("/api/vaccinations/" + recordId)
                .contentType(MediaType.APPLICATION_JSON).content(body("Booster", "null")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(records, pets);
        when(pets.find(petId, ownerId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(put("/api/vaccinations/" + recordId).requestAttr(AuthTokenFilter.OWNER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON).content(body("Booster", "null")))
                .andExpect(status().isNotFound());
        verify(records, never()).save(any());
        assertEquals("Rabies", record.getVaccineName());
    }

    @Test
    void rejectsMissingRecordsAndInvalidFields() throws Exception {
        mvc.perform(put("/api/vaccinations/" + UUID.randomUUID()).requestAttr(AuthTokenFilter.OWNER_ID, ownerId)
                .contentType(MediaType.APPLICATION_JSON).content(body("Booster", "null")))
                .andExpect(status().isNotFound());
        for (String invalid : new String[] { body("   ", "null"), body("x".repeat(121), "null"),
                body("Booster", "\"2026-10-10\""), body("Booster", "\"2026-10-09\""),
                "{\"vaccineName\":\"Booster\"}" }) {
            mvc.perform(put("/api/vaccinations/" + recordId).requestAttr(AuthTokenFilter.OWNER_ID, ownerId)
                    .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verify(records, never()).save(any());
        assertEquals("Rabies", record.getVaccineName());
    }
}
