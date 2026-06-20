ALTER TABLE platform_event_outbox
    ADD COLUMN claim_token varchar(128) NULL,
    ADD COLUMN claim_expires_at datetime NULL;

CREATE INDEX idx_platform_event_outbox_claim_token ON platform_event_outbox (claim_token);
