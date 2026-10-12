package com.pettracker.features.extraction;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class InterpretationServiceTest {
    private final InterpretationService service = new InterpretationService();

    @Test void extractsLabelledFieldsWithoutConfusingBirthAndDueDates() {
        var result = service.interpret("Owner: Sam\nHappy Paws Clinic\nBirth date: 2020-01-01\nRabies\nAdministered: 2026-10-05\nNext vaccination date: 2027-10-05");
        assertEquals("Happy Paws Clinic", result.clinicName());
        assertEquals("RABIES", result.vaccineName());
        assertEquals(LocalDate.of(2026, 10, 5), result.administeredOn());
        assertEquals(LocalDate.of(2027, 10, 5), result.nextDueOn());
        assertEquals(4, result.warnings().size());
    }
    @Test void rejectsAmbiguousAndImpossibleDates() {
        assertNull(service.interpret("Administered: 05/10/2026").administeredOn());
        assertTrue(service.interpret("Administered: 05/10/2026").warnings().get("administeredOn").contains("Ambiguous"));
        assertNull(service.interpret("Administered: 2026-02-30").administeredOn());
        assertEquals(LocalDate.of(2026, 10, 25), service.interpret("Given on: 25/10/2026").administeredOn());
    }
    @Test void leavesUnlabelledAndConflictingValuesForManualReview() {
        var result = service.interpret("2026-10-05\n2027-10-05\nRabies DHPP");
        assertNull(result.administeredOn());
        assertNull(result.nextDueOn());
        assertNull(result.vaccineName());
        assertNull(service.interpret("Administered: 2026-10-05\nDue: 2026-09-01").nextDueOn());
        assertNull(service.interpret(null).clinicName());
    }
}
