package com.pettracker.features.reminders;

import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class VaccinationReminderJobTest {
    @Test void usesConfiguredLocalDateAndContinuesAfterDeliveryFailure() {
        var repository = mock(ReminderRepository.class);
        var delivery = mock(ReminderDeliveryService.class);
        // UTC is still October 10, while Kolkata is already October 11.
        var clock = Clock.fixed(Instant.parse("2026-10-10T23:45:00Z"), ZoneId.of("Asia/Kolkata"));
        var target = LocalDate.of(2026, 10, 18);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(repository.findOwnersDueOn(target)).thenReturn(List.of(first, second));
        when(delivery.send(first, target)).thenThrow(new IllegalStateException("SMTP unavailable"));
        when(delivery.send(second, target)).thenReturn(ReminderDeliveryService.DeliveryResult.SENT);
        new VaccinationReminderJob(clock, repository, delivery).runDaily();
        verify(repository).findOwnersDueOn(target);
        verify(delivery).send(second, target);
    }
    @Test void sendsNothingWhenThereAreNoMatchingOwners() {
        var repository = mock(ReminderRepository.class);
        var delivery = mock(ReminderDeliveryService.class);
        var clock = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
        when(repository.findOwnersDueOn(any())).thenReturn(List.of());
        new VaccinationReminderJob(clock, repository, delivery).runDaily();
        verifyNoInteractions(delivery);
    }
    @Test void reportsConsolePreviewsSeparatelyFromSentEmails() {
        var repository = mock(ReminderRepository.class);
        var delivery = mock(ReminderDeliveryService.class);
        var owner = UUID.randomUUID();
        when(repository.findOwnersDueOn(any())).thenReturn(List.of(owner));
        when(delivery.send(eq(owner), any())).thenReturn(ReminderDeliveryService.DeliveryResult.PRINTED);
        var result = new VaccinationReminderJob(Clock.systemUTC(), repository, delivery).run();
        org.junit.jupiter.api.Assertions.assertEquals(1, result.printed());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.sent());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.failed());
    }
}
