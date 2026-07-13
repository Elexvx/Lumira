package com.lumira.saas.modules.system.online;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OnlineSessionUserRepository {
    List<UserRecord> findByIds(Collection<Long> userIds);
    Optional<UserRecord> findById(Long userId);
    int disable(UserRecord expectedUser, Long updatedBy, String updatedByUuid, LocalDateTime updatedAt);

    class UserRecord {
        private Long id;
        private String uuid;
        private String username;
        private String nickname;
        private String realName;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getRealName() { return realName; }
        public void setRealName(String realName) { this.realName = realName; }
    }
}
