package com.lumira.saas.modules.competition.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionManagementAppServiceTest {

    @Test
    void deleteCompetitionRejectsCompetitionWithRegistrations() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("from competition_registration"), eq(Long.class), eq(11L))).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteCompetition(admin(), 11L))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getUserMessage()).contains("不能删除");
                });
        verify(jdbcTemplate, never()).update(contains("update aiadc_competition"), any());
    }

    @Test
    void deleteCompetitionSoftDeletesWhenNoRegistrationsExist() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        CompetitionManagementAppService service = service(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("from competition_registration"), eq(Long.class), eq(11L))).thenReturn(0L);
        when(jdbcTemplate.update(contains("update aiadc_competition"), eq(1001L), any(), eq(11L))).thenReturn(1);

        assertThat(service.deleteCompetition(admin(), 11L)).isTrue();
        verify(jdbcTemplate).update(contains("update aiadc_competition"), eq(1001L), any(), eq(11L));
    }

    private CompetitionManagementAppService service(MyBatisQueryOperations jdbcTemplate) {
        return new CompetitionManagementAppService(jdbcTemplate, mock(DictRuntimeService.class));
    }

    private CurrentUser admin() {
        return new CurrentUser(1001L, "admin", "session", 1, true, Set.of("aiadc:competition:delete"));
    }
}
