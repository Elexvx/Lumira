package com.lumira.team.app;

import com.lumira.common.exception.BizException;
import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import com.lumira.team.infrastructure.persistence.RowMapper;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamPermissionServiceTest {
    @Test
    void roleBoundariesShouldMatchTeamRules() {
        TeamPermissionService service = new TeamPermissionService(new PermissionQueries("OWNER"));

        service.requireTeamOwner(2001L, 3001L, "user-uuid-3001");
        assertThat(service.canInvite("OWNER")).isTrue();
        assertThat(service.canInvite("ADMIN")).isTrue();
        assertThat(service.canInvite("MEMBER")).isFalse();
        assertThat(service.canUpdateTeam("MANAGER")).isTrue();
        assertThat(service.canDisbandTeam("ADMIN")).isFalse();
        assertThat(service.canRemoveMember("ADMIN", "OWNER", false)).isFalse();
        assertThat(service.canRemoveMember("ADMIN", "ADMIN", false)).isFalse();
        assertThat(service.canRemoveMember("ADMIN", "ADMIN", true)).isTrue();
        assertThat(service.canRemoveMember("ADMIN", "MANAGER", false)).isTrue();
        assertThat(service.canRemoveMember("MANAGER", "ADMIN", false)).isFalse();
        assertThat(service.canRemoveMember("MANAGER", "MEMBER", false)).isTrue();
        assertThat(service.canRemoveMember("MANAGER", "MANAGER", true)).isTrue();
        assertThat(service.canRemoveMember("MEMBER", "MEMBER", true)).isTrue();
    }

    @Test
    void memberCannotInviteOrRequireAdmin() {
        TeamPermissionService service = new TeamPermissionService(new PermissionQueries("MEMBER"));

        assertThatThrownBy(() -> service.requireTeamAdmin(2001L, 3001L, "user-uuid-3001"))
                .isInstanceOf(BizException.class);
        assertThat(service.canInvite("MEMBER")).isFalse();
    }

    @Test
    void permissionLookupShouldRejectInvalidIdsBeforeQuerying() {
        PermissionQueries queries = new PermissionQueries("OWNER");
        TeamPermissionService service = new TeamPermissionService(queries);

        assertThatThrownBy(() -> service.activeMember(0L, 3001L, "user-uuid-3001"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.requireTeamMember(2001L, null, "user-uuid-3001"))
                .isInstanceOf(BizException.class);

        assertThat(queries.queryCount).isZero();
    }

    private static final class PermissionQueries extends MyBatisQueryOperations {
        private final String role;
        private int queryCount;

        private PermissionQueries(String role) {
            this.role = role;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount += 1;
            TeamVO.Member member = new TeamVO.Member();
            member.setId(1L);
            member.setTeamId(2001L);
            member.setUserId(3001L);
            member.setUserUuid("user-uuid-3001");
            member.setRole(role);
            member.setStatus("ACTIVE");
            return cast(List.of(member));
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> cast(List<?> value) {
            return (List<T>) new ArrayList<>(value);
        }
    }
}
