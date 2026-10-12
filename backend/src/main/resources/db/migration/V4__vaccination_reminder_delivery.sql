CREATE TABLE vaccination_reminder_delivery (
    vaccination_id UUID NOT NULL REFERENCES vaccination_records(id),
    due_on DATE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vaccination_id, due_on)
);

CREATE INDEX idx_vaccination_next_due_on ON vaccination_records(next_due_on)
    WHERE next_due_on IS NOT NULL;
