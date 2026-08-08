package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.repository.AiPlatformQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPlatformQueryFacadeTest {

    @Test
    void shouldReadMenusAndConfigThroughRepository() {
        AiPlatformQueryRepository repository = mock(AiPlatformQueryRepository.class);
        CurrentUser actor = new CurrentUser();
        actor.setUserId(100L);
        List<Map<String, Object>> menus = List.of(Map.of("menuCode", "system.user"));
        Map<String, Object> config = Map.of("configKey", "security.login");
        when(repository.findMenus(actor, "ENABLED", 20)).thenReturn(menus);
        when(repository.findConfig(actor, "security.login")).thenReturn(Optional.of(config));
        DefaultAiPlatformQueryFacade facade = new DefaultAiPlatformQueryFacade(repository);

        assertThat(facade.listMenus(actor, "ENABLED", 20)).isSameAs(menus);
        assertThat(facade.readConfig(actor, "security.login")).isSameAs(config);
        verify(repository).findMenus(actor, "ENABLED", 20);
        verify(repository).findConfig(actor, "security.login");
    }
}
