CREATE TABLE vaccination_certificates (
    vaccination_id UUID PRIMARY KEY
        REFERENCES vaccination_records(id),
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL
        CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);