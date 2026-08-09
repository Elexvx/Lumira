package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.api.ai.AiSystemReadPort;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.repository.AiIamUserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiIamUserRepository implements AiIamUserRepository {

    private final AiSystemReadPort systemReadPort;

    public JdbcAiIamUserRepository(AiSystemReadPort systemReadPort) {
        this.systemReadPort = systemReadPort;
    }

    @Override
    public UserSearch search(CurrentUser actor, String keyword, String status, int limit) {
        AiSystemReadPort.UserSearchPage page = systemReadPort.searchUsers(actor, keyword, status, limit);
        if (page == null) {
            return new UserSearch(List.of(), 0L);
        }
        List<Map<String, Object>> items = page.items().stream()
                .map(user -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", user.id());
                    item.put("username", user.username());
                    item.put("nickname", user.nickname());
                    item.put("realName", user.realName());
                    item.put("mobile", user.mobile());
                    item.put("email", user.email());
                    item.put("status", user.status());
                    item.put("createdAt", user.createdAt());
                    item.put("updatedAt", user.updatedAt());
                    return item;
                })
                .toList();
        return new UserSearch(items, page.total());
    }
}
