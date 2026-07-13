-- Immutable collection schema snapshot used to render historical registrations
-- after administrators change or delete configurable fields.
ALTER TABLE `competition_registration`
    ADD COLUMN `collection_schema_snapshot_json` longtext NULL AFTER `member_snapshot_json`;
