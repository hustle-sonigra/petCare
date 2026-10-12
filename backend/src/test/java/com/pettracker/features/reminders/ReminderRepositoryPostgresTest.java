package com.pettracker.features.reminders;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Temporary tables shadow app tables. Nothing is written to the application's records. */
@EnabledIfEnvironmentVariable(named = "REMINDER_DB_TEST", matches = "true")
class ReminderRepositoryPostgresTest {
    @Test void queriesExactDueDateAndExcludesArchivedNullAndSentRecords() throws Exception {
        try (var connection = DriverManager.getConnection(
                System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://127.0.0.1:5433/pet_tracker"),
                System.getenv().getOrDefault("DB_USERNAME", "pet_tracker"),
                System.getenv().getOrDefault("DB_PASSWORD", "local-dev-only"))) {
            connection.setAutoCommit(false);
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                jdbc.execute("CREATE TEMP TABLE owner (id UUID PRIMARY KEY, name TEXT, email TEXT) ON COMMIT DROP");
                jdbc.execute("CREATE TEMP TABLE pets (id UUID PRIMARY KEY, owner_id UUID, name TEXT, archived_at TIMESTAMPTZ) ON COMMIT DROP");
                jdbc.execute("CREATE TEMP TABLE vaccination_records (id UUID PRIMARY KEY, pet_id UUID, vaccine_name TEXT, next_due_on DATE) ON COMMIT DROP");
                jdbc.execute("CREATE TEMP TABLE vaccination_reminder_delivery (vaccination_id UUID, due_on DATE, PRIMARY KEY(vaccination_id,due_on)) ON COMMIT DROP");
                UUID owner = UUID.randomUUID(), pet = UUID.randomUUID(), archived = UUID.randomUUID();
                UUID match = UUID.randomUUID(), sent = UUID.randomUUID();
                LocalDate due = LocalDate.of(2026, 10, 18);
                jdbc.update("INSERT INTO owner VALUES (?, 'Sam', 'sam@example.invalid')", owner);
                jdbc.update("INSERT INTO pets VALUES (?, ?, 'Milo', NULL), (?, ?, 'Archived', CURRENT_TIMESTAMP)", pet, owner, archived, owner);
                jdbc.update("INSERT INTO vaccination_records VALUES (?, ?, 'Rabies', ?), (?, ?, 'DHPP', ?)", match, pet, due, sent, pet, due);
                for (LocalDate other : new LocalDate[]{due.minusDays(1), due.plusDays(1), null})
                    jdbc.update("INSERT INTO vaccination_records VALUES (?, ?, 'Other', ?)", UUID.randomUUID(), pet, other);
                jdbc.update("INSERT INTO vaccination_records VALUES (?, ?, 'Archived vaccine', ?)", UUID.randomUUID(), archived, due);
                jdbc.update("INSERT INTO vaccination_reminder_delivery VALUES (?, ?)", sent, due);
                var repository = new ReminderRepository(jdbc);
                assertEquals(java.util.List.of(owner), repository.findOwnersDueOn(due));
                repository.lockOwner(owner);
                var matches = repository.findPending(owner, due);
                assertEquals(1, matches.size());
                assertEquals(match, matches.getFirst().vaccinationId());
                assertEquals("sam@example.invalid", matches.getFirst().email());
                repository.markSent(match, due);
                assertTrue(repository.findOwnersDueOn(due).isEmpty());
                assertTrue(repository.findPending(owner, due).isEmpty());
            } finally { connection.rollback(); }
        }
    }
}
