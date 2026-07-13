package com.lumira.team;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.app.TeamInternalApiService;
import com.lumira.team.app.TeamPermissionService;
import com.lumira.team.controller.InternalTeamController;
import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InternalTeamAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void monolithKeepsLocalInternalApiButDoesNotExposeController() {
        contextRunner.withPropertyValues("lumira.monolith=true").run(context -> {
            assertThat(context.getBeansOfType(TeamInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(TeamInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalTeamController.class)).isEmpty();
        });
    }

    @Test
    void splitRuntimeExposesControllerAndLocalInternalApi() {
        contextRunner.withPropertyValues("lumira.monolith=false").run(context -> {
            assertThat(context.getBeansOfType(TeamInternalApi.class)).hasSize(1);
            assertThat(context.getBeansOfType(TeamInternalApiService.class)).hasSize(1);
            assertThat(context.getBeansOfType(InternalTeamController.class)).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({TeamInternalApiService.class, InternalTeamController.class})
    static class TestConfiguration {

        @Bean
        MyBatisQueryOperations myBatisQueryOperations() {
            return mock(MyBatisQueryOperations.class);
        }

        @Bean
        TeamPermissionService teamPermissionService() {
            return mock(TeamPermissionService.class);
        }

        @Bean
        TeamRepository teamRepository() {
            return mock(TeamRepository.class);
        }

        @Bean
        TeamMemberRepository teamMemberRepository() {
            return mock(TeamMemberRepository.class);
        }

        @Bean
        ObjectProvider<SystemInternalApi> systemInternalApi() {
            SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
            return new org.springframework.beans.factory.ObjectProvider<>() {
                @Override
                public SystemInternalApi getObject(Object... args) {
                    return systemInternalApi;
                }

                @Override
                public SystemInternalApi getIfAvailable() {
                    return systemInternalApi;
                }

                @Override
                public SystemInternalApi getIfUnique() {
                    return systemInternalApi;
                }

                @Override
                public SystemInternalApi getObject() {
                    return systemInternalApi;
                }

                @Override
                public java.util.Iterator<SystemInternalApi> iterator() {
                    return java.util.List.of(systemInternalApi).iterator();
                }
            };
        }
    }
}
