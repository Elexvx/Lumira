package com.lumira.auth.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import java.time.Instant;
import java.util.Map;

public final class AuthDomainModels {

    private AuthDomainModels() {
    }

    public static final class AuthSessionAggregate extends AggregateRoot<String> {
        private final Long userId;
        private final String userUuid;
        private Instant lastActiveAt;
        private boolean revoked;

        public AuthSessionAggregate(String sessionId, Long userId, String userUuid, Instant lastActiveAt) {
            super(EntityId.of(sessionId));
            if (userId == null || userId <= 0 || userUuid == null || userUuid.isBlank()) {
                throw new IllegalArgumentException("trusted session user identity is required");
            }
            this.userId = userId;
            this.userUuid = userUuid.trim();
            this.lastActiveAt = lastActiveAt == null ? Instant.now() : lastActiveAt;
        }

        public void touch(Instant now) {
            if (revoked) {
                throw new IllegalStateException("revoked session cannot be refreshed");
            }
            lastActiveAt = now == null ? Instant.now() : now;
            registerEvent(StandardDomainEvent.of(
                    "AUTH_SESSION_REFRESHED",
                    "auth.session",
                    id().value(),
                    actorAttributes(Map.of("lastActiveAt", lastActiveAt.toString()))
            ));
        }

        public void revoke(String reason) {
            if (revoked) {
                return;
            }
            revoked = true;
            registerEvent(StandardDomainEvent.of(
                    "AUTH_SESSION_REVOKED",
                    "auth.session",
                    id().value(),
                    actorAttributes(Map.of("reason", reason == null ? "unspecified" : reason))
            ));
        }

        private Map<String, Object> actorAttributes(Map<String, Object> attributes) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>(attributes);
            result.put("userId", userId);
            result.put("userUuid", userUuid);
            return result;
        }
    }

    public record LoginChallenge(String challengeId, String purpose, Instant expiresAt) {

        public boolean isExpired(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now == null ? Instant.now() : now);
        }
    }
}
