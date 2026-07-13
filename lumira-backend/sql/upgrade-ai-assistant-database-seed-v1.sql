INSERT INTO `ai_employee` (
  `username`, `nickname`, `position`, `avatar_key`, `description`, `greeting`, `system_prompt`,
  `default_llm_service_id`, `enabled`, `sort_order`, `is_deleted`, `create_time`, `update_time`
) VALUES (
  'ai-assistant', 'AI Assistant', 'General Chat', NULL,
  'Default assistant for general AI conversations.', 'Hello, I am AI Assistant. How can I help?',
  'You are the general AI assistant for this enterprise platform. Help clearly and concisely, answer in the user''s language, and do not claim access to a specific digital employee''s private skills or knowledge unless one is selected.',
  NULL, 1, 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON DUPLICATE KEY UPDATE `is_deleted` = 0;
