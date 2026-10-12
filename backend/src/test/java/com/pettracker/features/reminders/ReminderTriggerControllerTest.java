package com.pettracker.features.reminders;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReminderTriggerControllerTest {
    @Test void requiresKeyAndPostAndReturnsRunCounts() throws Exception {
        var job = mock(VaccinationReminderJob.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ReminderTriggerController(job, "test-secret")).build();
        String url = "/api/reminders/vaccinations/run";
        mvc.perform(get(url)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("X-Reminder-Key", "wrong")).andExpect(status().isUnauthorized());
        verifyNoInteractions(job);
        when(job.run()).thenReturn(new VaccinationReminderJob.RunResult(LocalDate.of(2026, 10, 18), 0, 2, 0, 0));
        mvc.perform(post(url).header("X-Reminder-Key", "test-secret"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.printed").value(2))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(job).run();
    }

    @Test void disablesUnconfiguredTriggerAndSignalsBatchFailure() throws Exception {
        var job = mock(VaccinationReminderJob.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ReminderTriggerController(job, "")).build();
        mvc.perform(post("/api/reminders/vaccinations/run")).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(job);
        mvc = MockMvcBuilders.standaloneSetup(new ReminderTriggerController(job, "test-secret")).build();
        when(job.run()).thenReturn(new VaccinationReminderJob.RunResult(LocalDate.of(2026, 10, 18), 0, 0, 0, 1));
        mvc.perform(post("/api/reminders/vaccinations/run").header("X-Reminder-Key", "test-secret"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.failed").value(1));
    }
}
