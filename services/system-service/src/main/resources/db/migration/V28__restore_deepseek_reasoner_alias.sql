UPDATE ai_llm_service
SET default_model = 'deepseek-reasoner',
    update_time = CURRENT_TIMESTAMP
WHERE provider = 'deepseek'
  AND is_deleted = 0
  AND default_model = 'deepseek-v4-pro';
