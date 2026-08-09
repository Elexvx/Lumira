package com.lumira.saas.modules.ai.repository;

import com.lumira.common.security.CurrentUser;
import java.util.List;
import java.util.Map;

public interface AiIamUserRepository {

    UserSearch search(CurrentUser actor, String keyword, String status, int limit);

    record UserSearch(List<Map<String, Object>> items, long total) {
        public UserSearch {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
