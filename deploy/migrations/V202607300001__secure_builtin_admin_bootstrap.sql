SET @schema_name = DATABASE();
SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'iam_user_credential'
          AND column_name = 'password_change_required'
    ),
    'SELECT 1',
    'ALTER TABLE iam_user_credential ADD COLUMN password_change_required tinyint NOT NULL DEFAULT 0 AFTER last_changed_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `platform_bootstrap_credential` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `principal_key` varchar(64) NOT NULL,
    `user_id` bigint NOT NULL,
    `user_uuid` char(36) NOT NULL,
    `initialization_source` varchar(32) NOT NULL,
    `password_change_required` tinyint NOT NULL DEFAULT '0',
    `initialized_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_bootstrap_principal` (`principal_key`),
    KEY `idx_platform_bootstrap_user` (`user_id`, `user_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Preserve already-rotated installations. The marker prevents any later
-- bootstrap run from replacing an operator-managed credential.
INSERT INTO `platform_bootstrap_credential` (
    `principal_key`,
    `user_id`,
    `user_uuid`,
    `initialization_source`,
    `password_change_required`
)
SELECT
    'BUILTIN_ADMIN',
    user_row.`id`,
    user_row.`uuid`,
    'EXISTING_CREDENTIAL',
    0
FROM `sys_user` user_row
WHERE user_row.`id` = 1001
  AND user_row.`deleted` = 0
  AND user_row.`password_hash` <> ''
  AND user_row.`password_hash` <> CONCAT(
      '$2a$',
      '10$',
      'VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te'
  )
  AND EXISTS (
      SELECT 1
      FROM `iam_user_credential` credential
      WHERE credential.`user_id` = user_row.`id`
        AND credential.`user_uuid` = user_row.`uuid`
        AND credential.`credential_type` = 'PASSWORD'
        AND credential.`credential_secret` = user_row.`password_hash`
        AND credential.`status` = 'ENABLED'
        AND credential.`deleted` = 0
  )
ON DUPLICATE KEY UPDATE
    `principal_key` = VALUES(`principal_key`);

-- Invalidate only the two known repository seed credentials. User-created
-- and already-rotated credentials are never changed by this migration.
UPDATE `iam_user_credential`
SET `credential_secret` = '',
    `password_change_required` = 0,
    `status` = 'DISABLED',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `user_id` IN (1001, 1002)
  AND `credential_type` = 'PASSWORD'
  AND `credential_secret` = CONCAT(
      '$2a$',
      '10$',
      'VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te'
  )
  AND `deleted` = 0;

UPDATE `sys_user`
SET `password_hash` = '',
    `status` = 'DISABLED',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `id` IN (1001, 1002)
  AND `password_hash` = CONCAT(
      '$2a$',
      '10$',
      'VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te'
  )
  AND `deleted` = 0;

-- The repository-provided ordinary "user" account is never a production
-- identity. Preserve its credential for audit/recovery, but disable every
-- representation even when an operator previously rotated its password.
UPDATE `sys_user`
SET `status` = 'DISABLED',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `id` = 1002
  AND `deleted` = 0;

UPDATE `iam_user_credential`
SET `password_change_required` = 0,
    `status` = 'DISABLED',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `user_id` = 1002
  AND `deleted` = 0;

UPDATE `iam_user` iam
JOIN `sys_user` user_row ON user_row.`id` = iam.`id`
SET iam.`status` = 'DISABLED',
    iam.`updated_at` = CURRENT_TIMESTAMP
WHERE iam.`id` IN (1001, 1002)
  AND (
      iam.`id` = 1002
      OR (user_row.`password_hash` = '' AND user_row.`status` = 'DISABLED')
  )
  AND iam.`deleted` = 0;

UPDATE `iam_user_identity` identity_row
JOIN `sys_user` user_row
  ON user_row.`id` = identity_row.`user_id`
 AND user_row.`uuid` = identity_row.`user_uuid`
SET identity_row.`status` = 'DISABLED',
    identity_row.`updated_at` = CURRENT_TIMESTAMP
WHERE identity_row.`user_id` IN (1001, 1002)
  AND identity_row.`identity_type` = 'USERNAME'
  AND (
      identity_row.`user_id` = 1002
      OR (user_row.`password_hash` = '' AND user_row.`status` = 'DISABLED')
  )
  AND identity_row.`deleted` = 0;

UPDATE `iam_subject` subject_row
JOIN `sys_user` user_row ON user_row.`id` = subject_row.`ref_id`
SET subject_row.`status` = 'DISABLED',
    subject_row.`updated_at` = CURRENT_TIMESTAMP
WHERE subject_row.`subject_type` = 'USER'
  AND subject_row.`ref_id` IN (1001, 1002)
  AND (
      subject_row.`ref_id` = 1002
      OR (user_row.`password_hash` = '' AND user_row.`status` = 'DISABLED')
  )
  AND subject_row.`deleted` = 0;
