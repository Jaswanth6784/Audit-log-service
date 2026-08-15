CREATE INDEX idx_audit_event_retention_candidates
    ON audit_event (archived_at, recorded_at, sequence_number);
