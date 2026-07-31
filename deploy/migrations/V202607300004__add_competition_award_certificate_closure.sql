CREATE TABLE IF NOT EXISTS competition_award_grant (
  id bigint NOT NULL AUTO_INCREMENT,
  publication_id bigint NOT NULL,
  publication_version int NOT NULL,
  review_batch_id bigint NOT NULL,
  competition_id bigint NOT NULL,
  stage_id bigint NOT NULL,
  candidate_id bigint NOT NULL,
  registration_id bigint NOT NULL,
  project_id bigint NOT NULL,
  team_id bigint NOT NULL,
  user_id bigint NOT NULL,
  user_uuid char(36) NOT NULL,
  recipient_name varchar(128) NOT NULL,
  competition_title varchar(128) NOT NULL,
  project_name varchar(128) DEFAULT NULL,
  team_name varchar(128) DEFAULT NULL,
  award_name varchar(128) NOT NULL,
  rank_no int DEFAULT NULL,
  decision varchar(32) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'GRANTED',
  certificate_record_id bigint DEFAULT NULL,
  granted_at datetime NOT NULL,
  created_by bigint NOT NULL,
  created_by_uuid char(36) DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by bigint NOT NULL,
  updated_by_uuid char(36) DEFAULT NULL,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_competition_award_grant (publication_id, candidate_id, deleted),
  UNIQUE KEY uk_competition_award_certificate (certificate_record_id, deleted),
  KEY idx_competition_award_batch (review_batch_id, status, deleted),
  KEY idx_competition_award_user (user_id, user_uuid, status, deleted),
  KEY idx_competition_award_registration (registration_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @certificate_registration_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'certificate_record'
    AND index_name = 'idx_certificate_record_registration'
);
SET @certificate_registration_index_sql := IF(
  @certificate_registration_index_exists = 0,
  'ALTER TABLE certificate_record ADD INDEX idx_certificate_record_registration (registration_id, deleted)',
  'SELECT 1'
);
PREPARE certificate_registration_index_statement FROM @certificate_registration_index_sql;
EXECUTE certificate_registration_index_statement;
DEALLOCATE PREPARE certificate_registration_index_statement;

SET @certificate_user_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'certificate_record'
    AND index_name = 'idx_certificate_record_user'
);
SET @certificate_user_index_sql := IF(
  @certificate_user_index_exists = 0,
  'ALTER TABLE certificate_record ADD INDEX idx_certificate_record_user (user_id, deleted, issue_date)',
  'SELECT 1'
);
PREPARE certificate_user_index_statement FROM @certificate_user_index_sql;
EXECUTE certificate_user_index_statement;
DEALLOCATE PREPARE certificate_user_index_statement;

SET @certificate_team_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'certificate_record'
    AND index_name = 'idx_certificate_record_team'
);
SET @certificate_team_index_sql := IF(
  @certificate_team_index_exists = 0,
  'ALTER TABLE certificate_record ADD INDEX idx_certificate_record_team (team_id, deleted, issue_date)',
  'SELECT 1'
);
PREPARE certificate_team_index_statement FROM @certificate_team_index_sql;
EXECUTE certificate_team_index_statement;
DEALLOCATE PREPARE certificate_team_index_statement;
