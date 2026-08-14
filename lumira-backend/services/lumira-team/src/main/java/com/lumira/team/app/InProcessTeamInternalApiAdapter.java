package com.lumira.team.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Adds the same internal-service trust boundary to in-process calls that an
 * HTTP client gets from X-Job-Token in split deployments.
 */
@Primary
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "true")
public class InProcessTeamInternalApiAdapter implements TeamInternalApi {

    private static final Authentication INTERNAL_SERVICE_AUTHENTICATION = internalServiceAuthentication();

    private final TeamInternalApiService delegate;

    public InProcessTeamInternalApiAdapter(TeamInternalApiService delegate) {
        this.delegate = delegate;
    }

    @Override
    public TeamSummaryDTO getTeam(Long requesterUserId, String requesterUserUuid, Long teamId) {
        return call(() -> delegate.getTeam(requesterUserId, requesterUserUuid, teamId));
    }

    @Override
    public List<TeamMemberDTO> listActiveMembers(Long requesterUserId, String requesterUserUuid, Long teamId) {
        return call(() -> delegate.listActiveMembers(requesterUserId, requesterUserUuid, teamId));
    }

    @Override
    public TeamMemberDTO requireActiveMember(Long teamId, Long userId, String userUuid) {
        return call(() -> delegate.requireActiveMember(teamId, userId, userUuid));
    }

    @Override
    public List<Long> listActiveTeamIdsForUser(Long userId, String userUuid) {
        return call(() -> delegate.listActiveTeamIdsForUser(userId, userUuid));
    }

    @Override
    public boolean isTeamOwner(Long teamId, Long userId, String userUuid) {
        return call(() -> delegate.isTeamOwner(teamId, userId, userUuid));
    }

    @Override
    public boolean isTeamAdmin(Long teamId, Long userId, String userUuid) {
        return call(() -> delegate.isTeamAdmin(teamId, userId, userUuid));
    }

    @Override
    public boolean isTeamManager(Long teamId, Long userId, String userUuid) {
        return call(() -> delegate.isTeamManager(teamId, userId, userUuid));
    }

    private <T> T call(TeamCall<T> call) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext temporaryContext = SecurityContextHolder.createEmptyContext();
        temporaryContext.setAuthentication(INTERNAL_SERVICE_AUTHENTICATION);
        SecurityContextHolder.setContext(temporaryContext);
        try {
            return call.execute();
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private static Authentication internalServiceAuthentication() {
        CurrentUser internalService = new CurrentUser(
                0L,
                "internal-service",
                null,
                "internal",
                0,
                false,
                java.util.Set.of()
        );
        return new UsernamePasswordAuthenticationToken(internalService, "internal-token", java.util.Set.of());
    }

    @FunctionalInterface
    private interface TeamCall<T> {
        T execute();
    }
}
