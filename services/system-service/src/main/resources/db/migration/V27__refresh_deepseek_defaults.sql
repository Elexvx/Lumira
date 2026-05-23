UPDATE ai_llm_service
SET base_url = 'https://api.deepseek.com',
    update_time = CURRENT_TIMESTAMP
WHERE provider = 'deepseek'
  AND is_deleted = 0
  AND base_url = 'https://api.deepseek.com/v1';
