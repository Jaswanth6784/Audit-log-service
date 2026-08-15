ALTER TABLE audit_event
    ADD COLUMN payload_proofs JSON NULL;

ALTER TABLE audit_event
    ADD COLUMN redacted_at TIMESTAMP WITH TIME ZONE NULL;
