SET NAMES utf8mb4;

ALTER TABLE `payment_order`
  MODIFY COLUMN `provider_order_no` varchar(128) NULL DEFAULT NULL;

UPDATE `payment_order`
SET `provider_order_no` = NULL
WHERE `provider_order_no` IS NOT NULL
  AND LEFT(
        `provider_order_no`,
        CHAR_LENGTH(CONCAT(`provider_code`, '-', `order_no`, '-'))
      ) = CONCAT(`provider_code`, '-', `order_no`, '-')
  AND CHAR_LENGTH(`provider_order_no`)
      = CHAR_LENGTH(CONCAT(`provider_code`, '-', `order_no`, '-')) + 12
  AND RIGHT(`provider_order_no`, 12) REGEXP '^[0-9a-f]{12}$';

UPDATE `payment_order` AS `payment`
JOIN `competition_registration` AS `registration`
  ON `registration`.`payment_order_no` = `payment`.`order_no`
 AND `registration`.`owner_user_id` = `payment`.`created_by`
 AND `registration`.`owner_user_uuid` IS NOT NULL
 AND `registration`.`deleted` = 0
SET `payment`.`created_by_uuid` = `registration`.`owner_user_uuid`
WHERE `payment`.`created_by_uuid` IS NULL
  AND `payment`.`deleted` = 0;
