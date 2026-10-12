package com.pettracker.features.reminders;

import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true")
public class ReminderConfiguration {
    @Bean Clock reminderClock(@Value("${app.reminders.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean ReminderSettings reminderSettings(@Value("${app.reminders.from}") String from,
                                           @Value("${app.reminders.app-url}") String appUrl,
                                           @Value("${spring.mail.host}") String smtpHost,
                                           @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean auth,
                                           @Value("${spring.mail.username:}") String username,
                                           @Value("${spring.mail.password:}") String password) {
        boolean smtpReady = !smtpHost.isBlank() && !from.isBlank()
                && !from.contains("\r") && !from.contains("\n")
                && (!auth || (!username.isBlank() && !password.isBlank()));
        URI uri = URI.create(appUrl);
        if (!java.util.Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("APP_URL must be an absolute HTTP(S) app address.");
        return new ReminderSettings(from, appUrl.replaceAll("/+$", "") + "/login", smtpReady);
    }

    public record ReminderSettings(String from, String loginUrl, boolean smtpReady) {
        public ReminderSettings(String from, String loginUrl) { this(from, loginUrl, true); }
    }
}
