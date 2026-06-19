package com.lumira.common.security.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataPermissionResolverTest {

    @Test
    void resolvesWildcardPermissionAsAllData() {
        DataPermissionDecision decision = DataPermissionResolver.resolve(
                "system:user",
                1001L,
                Set.of(),
                Set.of(),
                List.of(new DataPermissionRule("*", DataScopeType.SELF, List.of(), List.of())),
                Set.of("*")
        );

        assertThat(decision.scopeType()).isEqualTo(DataScopeType.ALL);
    }

    @Test
    void defaultsToSelfWhenNoRuleExists() {
        DataPermissionDecision decision = DataPermissionResolver.resolve(
                "file:object",
                1002L,
                Set.of(),
                Set.of(),
                List.of(),
                Set.of("system:file:view")
        );

        assertThat(decision.scopeType()).isEqualTo(DataScopeType.SELF);
        assertThat(decision.userIds()).containsExactly(1002L);
    }

    @Test
    void mergesDepartmentAndCustomScopes() {
        DataPermissionDecision decision = DataPermissionResolver.resolve(
                "system:user",
                1003L,
                Set.of(10L),
                Set.of(11L, 12L),
                List.of(
                        new DataPermissionRule("system:user", DataScopeType.DEPT_AND_CHILD, List.of(), List.of()),
                        new DataPermissionRule("system:user", DataScopeType.CUSTOM, List.of(20L), List.of(2001L))
                ),
                Set.of("system:user:view")
        );

        assertThat(decision.scopeType()).isEqualTo(DataScopeType.DEPT_AND_CHILD);
        assertThat(decision.deptIds()).containsExactlyInAnyOrder(10L, 11L, 12L, 20L);
        assertThat(decision.userIds()).containsExactly(2001L);
    }
}
