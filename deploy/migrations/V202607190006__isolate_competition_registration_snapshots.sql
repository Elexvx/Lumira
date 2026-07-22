-- Keep registration-entered values in the competition_registration row.
-- Existing registration values were nested in team_snapshot_json; move them to
-- their own snapshot column and leave the team snapshot team-only.
-- Future exports must scan this index in bounded id batches instead of loading
-- every snapshot through the offset-paginated registration list endpoint.

SET @schema_name = DATABASE();

SET @ddl = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'competition_registration'
      AND column_name = 'registration_snapshot_json'
  ),
  'SELECT 1',
  'ALTER TABLE competition_registration ADD COLUMN registration_snapshot_json longtext NULL AFTER participant_no'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'competition_registration'
      AND index_name = 'idx_competition_registration_export'
  ),
  'SELECT 1',
  'ALTER TABLE competition_registration ADD INDEX idx_competition_registration_export (competition_id, deleted, id)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `competition_registration`
SET `registration_snapshot_json` = CASE
        WHEN JSON_VALID(`team_snapshot_json`) THEN CASE
            WHEN JSON_TYPE(JSON_EXTRACT(`team_snapshot_json`, '$.registrationExtraValues')) = 'OBJECT'
                THEN JSON_EXTRACT(`team_snapshot_json`, '$.registrationExtraValues')
            ELSE JSON_OBJECT()
        END
        ELSE JSON_OBJECT()
    END,
    `team_snapshot_json` = CASE
        WHEN JSON_VALID(`team_snapshot_json`)
            THEN JSON_REMOVE(`team_snapshot_json`, '$.registrationExtraValues')
        ELSE `team_snapshot_json`
    END
WHERE `registration_snapshot_json` IS NULL;
