package com.pettracker.features.reminders;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pettracker.features.reminders.ReminderConfiguration.ReminderSettings;

@Service
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true")
public class ReminderDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryService.class);
    public enum DeliveryResult { SENT, PRINTED, SKIPPED }
    private final ReminderRepository repository;
    private final JavaMailSender mail;
    private final ReminderSettings settings;
    public ReminderDeliveryService(ReminderRepository repository, @Nullable JavaMailSender mail, ReminderSettings settings) {
        this.repository = repository; this.mail = mail; this.settings = settings;
    }

    @Transactional
    public DeliveryResult send(UUID ownerId, LocalDate dueOn) {
        repository.lockOwner(ownerId);
        var records = repository.findPending(ownerId, dueOn);
        if (records.isEmpty()) return DeliveryResult.SKIPPED;
        var owner = records.getFirst();
        String date = dueOn.format(DateTimeFormatter.ofPattern("d MMMM uuuu", java.util.Locale.ENGLISH));
        StringBuilder body = new StringBuilder("Hello " + owner.ownerName() + ",\n\n"
                + "These vaccinations are due in 7 days, on " + date + ":\n\n");
        for (var record : records) body.append("• ").append(record.petName()).append(" — ").append(record.vaccineName()).append('\n');
        body.append("\nPlease log into PetCare to review your pets' vaccination records and plan your clinic visit:\n")
                .append(settings.loginUrl()).append("\n\nPetCare\n");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from());
        message.setTo(owner.email());
        message.setSubject("PetCare: vaccinations due on " + date);
        message.setText(body.toString());
        if (!settings.smtpReady() || mail == null) {
            print(message);
            return DeliveryResult.PRINTED;
        }
        try { mail.send(message); }
        catch (MailException e) {
            log.warn("SMTP unavailable for reminder owner {}; printing the prepared email ({})", ownerId, e.getClass().getSimpleName());
            print(message);
            return DeliveryResult.PRINTED;
        }
        // Only accepted SMTP deliveries enter the ledger; console previews remain pending.
        for (var record : records) repository.markSent(record.vaccinationId(), dueOn);
        return DeliveryResult.SENT;
    }

    private void print(SimpleMailMessage message) {
        // Explicit console fallback requested by the user. Printed previews are not recorded as sent.
        log.info("Prepared reminder (not emailed)\nTo: {}\nSubject: {}\n\n{}",
                message.getTo()[0], message.getSubject(), message.getText());
    }
}
