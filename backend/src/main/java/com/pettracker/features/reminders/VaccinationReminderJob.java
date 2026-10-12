package com.pettracker.features.reminders;

import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true")
public class VaccinationReminderJob {
    private static final Logger log = LoggerFactory.getLogger(VaccinationReminderJob.class);
    private final Clock clock;
    private final ReminderRepository repository;
    private final ReminderDeliveryService delivery;
    public VaccinationReminderJob(Clock clock, ReminderRepository repository, ReminderDeliveryService delivery) {
        this.clock = clock; this.repository = repository; this.delivery = delivery;
    }

    @Scheduled(cron = "${app.reminders.cron}", zone = "${app.reminders.zone}")
    public void runDaily() {
        run();
    }

    public record RunResult(LocalDate dueOn, int sent, int printed, int skipped, int failed) {}

    public RunResult run() {
        LocalDate dueOn = LocalDate.now(clock).plusDays(7);
        int sent = 0, printed = 0, skipped = 0, failed = 0;
        for (var ownerId : repository.findOwnersDueOn(dueOn)) {
            try {
                switch (delivery.send(ownerId, dueOn)) {
                    case SENT -> sent++;
                    case PRINTED -> printed++;
                    case SKIPPED -> skipped++;
                }
            }
            catch (RuntimeException e) {
                failed++;
                // Avoid logging SMTP exception text that can contain email addresses or message bodies.
                log.error("Vaccination reminder failed for owner {} ({})", ownerId, e.getClass().getSimpleName());
            }
        }
        log.info("Vaccination reminders for {}: {} sent, {} printed, {} skipped, {} failed", dueOn, sent, printed, skipped, failed);
        return new RunResult(dueOn, sent, printed, skipped, failed);
    }
}
