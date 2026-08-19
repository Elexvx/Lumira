SET @schema_name = DATABASE();
SET @award_rules_json_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'competition_review_batch'
          AND column_name = 'award_rules_json'
    ),
    'SELECT 1',
    'ALTER TABLE `competition_review_batch` ADD COLUMN `award_rules_json` longtext NULL AFTER `candidate_count`'
);
PREPARE award_rules_json_statement FROM @award_rules_json_ddl;
EXECUTE award_rules_json_statement;
DEALLOCATE PREPARE award_rules_json_statement;
