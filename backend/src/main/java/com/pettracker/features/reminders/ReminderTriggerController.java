package com.pettracker.features.reminders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true")
@RequestMapping("/api/reminders/vaccinations")
public class ReminderTriggerController {
    private final VaccinationReminderJob job;
    private final String key;
    public ReminderTriggerController(VaccinationReminderJob job,
            @Value("${app.reminders.trigger-key:}") String key) {
        this.job = job; this.key = key;
    }

    // POST avoids browsers, crawlers and prefetchers accidentally triggering email delivery.
    @PostMapping("/run")
    public ResponseEntity<VaccinationReminderJob.RunResult> run(
            @RequestHeader(value = "X-Reminder-Key", required = false) String provided) {
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reminder trigger key is not configured.");
        if (provided == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var result = job.run();
        return ResponseEntity.status(result.failed() > 0 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result);
    }
}
