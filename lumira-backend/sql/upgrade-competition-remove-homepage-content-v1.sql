-- Manual destructive upgrade for existing databases.
--
-- The competition homepage field is no longer part of the API or domain
-- model. Back up the database before running this script: dropping the
-- column permanently removes any legacy homepage content.

SET @has_competition_homepage_column := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'aiadc_competition'
      AND column_name = 'homepage_content'
);

SET @drop_competition_homepage_sql := IF(
    @has_competition_homepage_column > 0,
    'ALTER TABLE `aiadc_competition` DROP COLUMN `homepage_content`',
    'SELECT 1'
);

PREPARE drop_competition_homepage_statement FROM @drop_competition_homepage_sql;
EXECUTE drop_competition_homepage_statement;
DEALLOCATE PREPARE drop_competition_homepage_statement;
