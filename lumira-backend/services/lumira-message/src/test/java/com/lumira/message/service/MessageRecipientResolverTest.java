package com.lumira.message.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.message.MessageNoticeDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageRecipientResolverTest {

    @Test
    void resolveRecipientUserIdsShouldUseSystemInternalApiWhenAvailable() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.userIdsByRole(3001L)).thenReturn(List.of(2001L, 2002L, 2001L));
        MessageRecipientResolver resolver = new MessageRecipientResolver(available(systemInternalApi));

        assertThat(resolver.resolveRecipientUserIds(roleNotice(3001L)))
                .containsExactly(2001L, 2002L);
    }

    @Test
    void resolveRecipientUserIdsShouldReturnEmptyWhenSystemInternalApiIsAbsent() {
        MessageRecipientResolver resolver = new MessageRecipientResolver(unavailable());

        assertThat(resolver.resolveRecipientUserIds(roleNotice(3001L))).isEmpty();
    }

    private MessageNoticeDTO roleNotice(Long roleId) {
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setTargetScope("ROLE");
        notice.setTargetRoleId(roleId);
        return notice;
    }

    private ObjectProvider<SystemInternalApi> available(SystemInternalApi systemInternalApi) {
        return new ObjectProvider<>() {
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
        };
    }

    private ObjectProvider<SystemInternalApi> unavailable() {
        return new ObjectProvider<>() {
            @Override
            public SystemInternalApi getObject(Object... args) {
                return null;
            }

            @Override
            public SystemInternalApi getIfAvailable() {
                return null;
            }

            @Override
            public SystemInternalApi getIfUnique() {
                return null;
            }

            @Override
            public SystemInternalApi getObject() {
                return null;
            }
        };
    }
}
