package com.lumira.saas.modules.ai.repository;

import java.util.List;
import java.util.Map;

public interface AiIamUserRepository {

    List<Map<String, Object>> search(String keyword, String status, int limit);

    long count(String keyword, String status);
}
