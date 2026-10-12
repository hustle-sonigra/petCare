package com.pettracker.features.reminders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import com.pettracker.features.reminders.ReminderRepository.ReminderRecord;
import com.pettracker.features.reminders.ReminderConfiguration.ReminderSettings;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ReminderDeliveryServiceTest {
    private final ReminderRepository repository = mock(ReminderRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final ReminderDeliveryService delivery = new ReminderDeliveryService(repository, mail,
            new ReminderSettings("reminders@example.invalid", "https://petcare.example.invalid/login"));
    private final UUID owner = UUID.randomUUID(), vaccination = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 10, 18);

    @Test void sendsOneCustomizedDigestThenRecordsDelivery() {
        UUID second = UUID.randomUUID();
        when(repository.findPending(owner, date)).thenReturn(List.of(
                new ReminderRecord(vaccination, "Sam", "sam@example.invalid", "Milo", "Rabies"),
                new ReminderRecord(second, "Sam", "sam@example.invalid", "Luna", "FVRCP")));
        assertEquals(ReminderDeliveryService.DeliveryResult.SENT, delivery.send(owner, date));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        var order = inOrder(repository, mail);
        order.verify(repository).lockOwner(owner);
        order.verify(repository).findPending(owner, date);
        order.verify(mail).send(message.capture());
        order.verify(repository).markSent(vaccination, date);
        order.verify(repository).markSent(second, date);
        assertArrayEquals(new String[]{"sam@example.invalid"}, message.getValue().getTo());
        assertTrue(message.getValue().getText().contains("Hello Sam"));
        assertTrue(message.getValue().getText().contains("Milo — Rabies"));
        assertTrue(message.getValue().getText().contains("Luna — FVRCP"));
        assertTrue(message.getValue().getText().contains("18 October 2026"));
        assertTrue(message.getValue().getText().contains("https://petcare.example.invalid/login"));
    }
    @Test void skipsAlreadyDeliveredRecordsAndDoesNotMarkSmtpFailuresAsSent() {
        when(repository.findPending(owner, date)).thenReturn(List.of());
        assertEquals(ReminderDeliveryService.DeliveryResult.SKIPPED, delivery.send(owner, date));
        verifyNoInteractions(mail);
        when(repository.findPending(owner, date)).thenReturn(List.of(
                new ReminderRecord(vaccination, "Sam", "sam@example.invalid", "Milo", "Rabies")));
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertEquals(ReminderDeliveryService.DeliveryResult.PRINTED, delivery.send(owner, date));
        verify(repository, never()).markSent(any(), any());
    }

    @Test void printsEmailWithoutSmtpAndLeavesItPending(CapturedOutput output) {
        when(repository.findPending(owner, date)).thenReturn(List.of(
                new ReminderRecord(vaccination, "Sam", "sam@example.invalid", "Milo", "Rabies")));
        var fallback = new ReminderDeliveryService(repository, null,
                new ReminderSettings("", "http://localhost:4200/login", false));
        assertEquals(ReminderDeliveryService.DeliveryResult.PRINTED, fallback.send(owner, date));
        assertTrue(output.getOut().contains("To: sam@example.invalid"));
        assertTrue(output.getOut().contains("Hello Sam"));
        assertTrue(output.getOut().contains("http://localhost:4200/login"));
        verifyNoInteractions(mail);
        verify(repository, never()).markSent(any(), any());
    }
}
