package com.lumira.api.ai;

import com.lumira.common.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Narrow System-owned read model required by AI native tools.
 *
 * <p>The records deliberately contain only the fields returned by the AI tools.  They are not
 * System DTO/VO types and do not make the System query service part of AI's API.</p>
 */
public interface AiSystemReadPort {

    UserSearchPage searchUsers(CurrentUser actor, String keyword, String status, int limit);

    List<MenuItem> findMenus(CurrentUser actor, String status, int limit);

    ConfigItem findConfig(CurrentUser actor, String configKey);

    record UserSearchPage(List<UserItem> items, long total) {
        public UserSearchPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record UserItem(
            Long id,
            String username,
            String nickname,
            String realName,
            String mobile,
            String email,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    record MenuItem(
            Long id,
            Long parentId,
            String menuCode,
            String menuName,
            String menuType,
            String path,
            String component,
            String permissionKey,
            String status,
            Integer sortNo
    ) {
    }

    record ConfigItem(String configKey, String configName, String configValue, Integer system) {
    }
}
