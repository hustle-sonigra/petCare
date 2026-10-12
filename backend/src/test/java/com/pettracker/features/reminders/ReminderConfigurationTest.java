package com.pettracker.features.reminders;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReminderConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ReminderConfiguration.class, VaccinationReminderJob.class,
                    ReminderDeliveryService.class, Mocks.class)
            .withPropertyValues("app.reminders.cron=0 0 9 * * *", "app.reminders.zone=Asia/Kolkata",
                    "app.reminders.from=reminders@example.invalid", "app.reminders.app-url=https://petcare.example.invalid",
                    "spring.mail.host=smtp.example.invalid");

    @Test void registersDailyCronAndMatchingDateTimezoneWhenEnabled() {
        runner.withPropertyValues("app.reminders.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(VaccinationReminderJob.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
            var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).hasSize(1);
            assertThat(((CronTask) tasks.iterator().next().getTask()).getExpression()).isEqualTo("0 0 9 * * *");
        });
    }
    @Test void supportsDisabledJobAndMissingSmtpConfiguration() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(VaccinationReminderJob.class));
        runner.withPropertyValues("app.reminders.enabled=true", "spring.mail.host=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ReminderConfiguration.ReminderSettings.class).smtpReady()).isFalse();
        });
    }
    @Test void canDisableInternalCronForExternalTriggering() {
        runner.withPropertyValues("app.reminders.enabled=true", "app.reminders.cron=-").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(VaccinationReminderJob.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
        });
    }
    @Configuration(proxyBeanMethods = false)
    static class Mocks {
        @Bean ReminderRepository repository() { return mock(ReminderRepository.class); }
        @Bean JavaMailSender sender() { return mock(JavaMailSender.class); }
    }
}
