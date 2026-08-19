ALTER TABLE `competition_review_batch`
    ADD COLUMN `award_rules_json` longtext NULL AFTER `candidate_count`;
