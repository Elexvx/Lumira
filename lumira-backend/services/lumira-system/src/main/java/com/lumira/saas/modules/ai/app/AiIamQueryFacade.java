package com.lumira.saas.modules.ai.app;

import com.lumira.saas.modules.ai.repository.AiIamUserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

interface AiIamQueryFacade {

    UserSearchResult searchUsers(String keyword, String status, int limit);

    record UserSearchResult(List<Map<String, Object>> items, long total) {
    }
}

@Service
class DefaultAiIamQueryFacade implements AiIamQueryFacade {

    private static final int MAX_SEARCH_LIMIT = 100;

    private final AiIamUserRepository userRepository;

    DefaultAiIamQueryFacade(AiIamUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserSearchResult searchUsers(String keyword, String status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        List<Map<String, Object>> users = userRepository.search(keyword, status, safeLimit).stream()
                .map(this::maskedUser)
                .toList();
        long total = users.size() < safeLimit
                ? users.size()
                : userRepository.count(keyword, status);
        return new UserSearchResult(users, total);
    }

    private Map<String, Object> maskedUser(Map<String, Object> source) {
        Map<String, Object> user = new LinkedHashMap<>(source);
        user.put("mobile", maskMobile(user.get("mobile")));
        user.put("email", maskEmail(user.get("email")));
        return user;
    }


    private String maskMobile(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() < 7) {
            return "***";
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    private String maskEmail(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        int at = text.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return text.charAt(0) + "***" + text.substring(at);
    }
}
