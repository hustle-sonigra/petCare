ALTER TABLE pets
    DROP CONSTRAINT fk_pets_owner,
    ADD CONSTRAINT fk_pets_owner FOREIGN KEY (owner_id)
        REFERENCES owner (id) ON DELETE CASCADE;

ALTER TABLE vaccination_records
    DROP CONSTRAINT vaccination_records_pet_id_fkey,
    ADD CONSTRAINT vaccination_records_pet_id_fkey FOREIGN KEY (pet_id)
        REFERENCES pets (id) ON DELETE CASCADE;

ALTER TABLE vaccination_certificates
    DROP CONSTRAINT vaccination_certificates_vaccination_id_fkey,
    ADD CONSTRAINT vaccination_certificates_vaccination_id_fkey FOREIGN KEY (vaccination_id)
        REFERENCES vaccination_records (id) ON DELETE CASCADE;

ALTER TABLE vaccination_reminder_delivery
    DROP CONSTRAINT vaccination_reminder_delivery_vaccination_id_fkey,
    ADD CONSTRAINT vaccination_reminder_delivery_vaccination_id_fkey FOREIGN KEY (vaccination_id)
        REFERENCES vaccination_records (id) ON DELETE CASCADE;
