package com.lumira.saas.modules.team.app;

import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamPermissionServiceTest {
    @Test
    void roleBoundariesShouldMatchTeamRules() {
        TeamPermissionService service = new TeamPermissionService(new PermissionQueries("OWNER"));

        service.requireTeamOwner(1001L, 2001L, 3001L);
        assertThat(service.canInvite("OWNER")).isTrue();
        assertThat(service.canInvite("ADMIN")).isTrue();
        assertThat(service.canInvite("MEMBER")).isFalse();
        assertThat(service.canUpdateTeam("MANAGER")).isTrue();
        assertThat(service.canDisbandTeam("ADMIN")).isFalse();
        assertThat(service.canRemoveMember("ADMIN", "OWNER", false)).isFalse();
        assertThat(service.canRemoveMember("MANAGER", "ADMIN", false)).isFalse();
        assertThat(service.canRemoveMember("MANAGER", "MEMBER", false)).isTrue();
        assertThat(service.canRemoveMember("MEMBER", "MEMBER", true)).isTrue();
    }

    @Test
    void memberCannotInviteOrRequireAdmin() {
        TeamPermissionService service = new TeamPermissionService(new PermissionQueries("MEMBER"));

        assertThatThrownBy(() -> service.requireTeamAdmin(1001L, 2001L, 3001L))
                .isInstanceOf(BizException.class);
        assertThat(service.canInvite("MEMBER")).isFalse();
    }

    private static final class PermissionQueries extends MyBatisQueryOperations {
        private final String role;

        private PermissionQueries(String role) {
            this.role = role;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            TeamVO.Member member = new TeamVO.Member();
            member.setId(1L);
            member.setTenantId(1001L);
            member.setTeamId(2001L);
            member.setUserId(3001L);
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
