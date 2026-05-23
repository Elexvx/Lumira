UPDATE ai_llm_service
SET base_url = 'https://api.deepseek.com',
    update_time = CURRENT_TIMESTAMP
WHERE provider = 'deepseek'
  AND is_deleted = 0
  AND base_url = 'https://api.deepseek.com/v1';

UPDATE ai_llm_service
SET default_model = 'deepseek-v4-flash',
    update_time = CURRENT_TIMESTAMP
WHERE provider = 'deepseek'
  AND is_deleted = 0
  AND default_model = 'deepseek-chat';

UPDATE ai_llm_service
SET default_model = 'deepseek-v4-pro',
    update_time = CURRENT_TIMESTAMP
WHERE provider = 'deepseek'
  AND is_deleted = 0
  AND default_model = 'deepseek-reasoner';
