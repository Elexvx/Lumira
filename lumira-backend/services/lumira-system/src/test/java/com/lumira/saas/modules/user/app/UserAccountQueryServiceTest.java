package com.lumira.saas.modules.user.app;

import com.lumira.saas.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UserAccountQueryServiceTest {

    @Test
    void findByIdRejectsNonPositiveUserIdBeforeMapperLookup() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        UserAccountQueryService service = new UserAccountQueryService(mapper);

        assertThat(service.findById(0L)).isEmpty();
        assertThat(service.findById(-1L)).isEmpty();

        verifyNoInteractions(mapper);
    }
}
