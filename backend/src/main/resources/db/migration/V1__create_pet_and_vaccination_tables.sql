CREATE TABLE pets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    species VARCHAR(20) NOT NULL,
    breed VARCHAR(100),
    birth_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_pets_owner_active_name
    ON pets (owner_id, archived_at, name);

CREATE TABLE vaccination_records (
    id UUID PRIMARY KEY,
    pet_id UUID NOT NULL REFERENCES pets (id),
    vaccine_name VARCHAR(120) NOT NULL,
    administered_on DATE NOT NULL,
    next_due_on DATE,
    provider VARCHAR(160),
    notes VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_vaccination_records_pet_administered
    ON vaccination_records (pet_id, administered_on DESC);

CREATE INDEX idx_vaccination_records_due_date
    ON vaccination_records (next_due_on)
    WHERE next_due_on IS NOT NULL;
