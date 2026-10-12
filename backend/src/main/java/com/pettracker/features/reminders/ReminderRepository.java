package com.pettracker.features.reminders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderRepository {
    private static final String ELIGIBLE = """
        FROM vaccination_records v
        JOIN pets p ON p.id = v.pet_id
        JOIN owner o ON o.id = p.owner_id
        WHERE v.next_due_on = ? AND p.archived_at IS NULL
          AND NOT EXISTS (
            SELECT 1 FROM vaccination_reminder_delivery d
            WHERE d.vaccination_id = v.id AND d.due_on = v.next_due_on
          )
        """;
    private final JdbcTemplate jdbc;
    public ReminderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<UUID> findOwnersDueOn(LocalDate date) {
        return jdbc.query("SELECT DISTINCT o.id " + ELIGIBLE,
                (rs, row) -> rs.getObject("id", UUID.class), date);
    }

    public List<ReminderRecord> findPending(UUID ownerId, LocalDate date) {
        return jdbc.query("SELECT v.id, o.name AS owner_name, o.email, p.name AS pet_name, v.vaccine_name "
                + ELIGIBLE + " AND o.id = ? ORDER BY p.name, v.vaccine_name, v.id",
                (rs, row) -> new ReminderRecord(rs.getObject("id", UUID.class), rs.getString("owner_name"),
                        rs.getString("email"), rs.getString("pet_name"), rs.getString("vaccine_name")), date, ownerId);
    }

    public void lockOwner(UUID ownerId) {
        // Transaction-scoped PostgreSQL lock prevents concurrent app instances sending the same digest.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "vaccination-reminder:" + ownerId);
    }

    public void markSent(UUID vaccinationId, LocalDate dueOn) {
        jdbc.update("INSERT INTO vaccination_reminder_delivery (vaccination_id, due_on) VALUES (?, ?)", vaccinationId, dueOn);
    }

    public record ReminderRecord(UUID vaccinationId, String ownerName, String email, String petName, String vaccineName) {}
}
