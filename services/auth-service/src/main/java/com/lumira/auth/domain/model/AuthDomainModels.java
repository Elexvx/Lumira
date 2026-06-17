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
        private final Long tenantId;
        private final Long userId;
        private Instant lastActiveAt;
        private boolean revoked;

        public AuthSessionAggregate(String sessionId, Long tenantId, Long userId, Instant lastActiveAt) {
            super(EntityId.of(sessionId));
            this.tenantId = tenantId;
            this.userId = userId;
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
                    tenantId,
                    Map.of("userId", userId, "lastActiveAt", lastActiveAt.toString())
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
                    tenantId,
                    Map.of("userId", userId, "reason", reason == null ? "unspecified" : reason)
            ));
        }
    }

    public record LoginChallenge(String challengeId, Long tenantId, String purpose, Instant expiresAt) {

        public boolean isExpired(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now == null ? Instant.now() : now);
        }
    }
}
