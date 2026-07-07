package com.lumira.message.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.message.MessageNoticeDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
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
        when(systemInternalApi.roleUserIdentities(3001L))
                .thenReturn(List.of(
                        user(2001L, "user-uuid-2001"),
                        user(2002L, "user-uuid-2002"),
                        user(2001L, "user-uuid-2001")
                ));
        MessageRecipientResolver resolver = new MessageRecipientResolver(available(systemInternalApi));

        assertThat(resolver.resolveRecipientUserIds(roleNotice(3001L)))
                .containsExactly(2001L, 2002L);
        assertThat(resolver.resolveRecipients(roleNotice(3001L)))
                .extracting(MessageRecipientResolver.Recipient::userUuid)
                .containsExactly("user-uuid-2001", "user-uuid-2002");
    }

    @Test
    void resolveRecipientsShouldUseTrustedUserIdentityForUserScope() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.userIdentitiesByIds(List.of(2001L))).thenReturn(List.of(user(2001L, "user-uuid-2001")));
        MessageRecipientResolver resolver = new MessageRecipientResolver(available(systemInternalApi));
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        notice.setTargetUserUuid("user-uuid-2001");

        assertThat(resolver.resolveRecipients(notice))
                .containsExactly(new MessageRecipientResolver.Recipient(2001L, "user-uuid-2001"));
    }

    @Test
    void resolveRecipientsShouldRejectUserScopeWhenTrustedIdentityUuidDiffers() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.userIdentitiesByIds(List.of(2001L))).thenReturn(List.of(user(2001L, "rotated-user-uuid")));
        MessageRecipientResolver resolver = new MessageRecipientResolver(available(systemInternalApi));
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        notice.setTargetUserUuid("user-uuid-2001");

        assertThat(resolver.resolveRecipients(notice)).isEmpty();
    }

    @Test
    void resolveRecipientsShouldRejectDisabledUserScopeRecipient() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.userIdentitiesByIds(List.of(2001L)))
                .thenReturn(List.of(user(2001L, "user-uuid-2001", "DISABLED")));
        MessageRecipientResolver resolver = new MessageRecipientResolver(available(systemInternalApi));
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        notice.setTargetUserUuid("user-uuid-2001");

        assertThat(resolver.resolveRecipients(notice)).isEmpty();
    }

    @Test
    void resolveRecipientUserIdsShouldFilterDisabledRoleRecipients() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.roleUserIdentities(3001L))
                .thenReturn(List.of(
                        user(2001L, "user-uuid-2001"),
                        user(2002L, "user-uuid-2002", "DISABLED"),
                        user(2003L, "user-uuid-2003")
                ));
        MessageRecipientResolver resolver = new MessageRecipientResolver(available(systemInternalApi));

        assertThat(resolver.resolveRecipientUserIds(roleNotice(3001L)))
                .containsExactly(2001L, 2003L);
    }

    @Test
    void resolveRecipientsShouldRejectUserScopeWithoutTargetUserUuid() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        MessageRecipientResolver resolver = new MessageRecipientResolver(available(systemInternalApi));
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);

        assertThat(resolver.resolveRecipients(notice)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(systemInternalApi);
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

    private SystemUserSnapshotDTO user(Long userId, String userUuid) {
        return user(userId, userUuid, "ENABLED");
    }

    private SystemUserSnapshotDTO user(Long userId, String userUuid, String status) {
        return new SystemUserSnapshotDTO(userId, userUuid, "user-" + userId, null, status, null, null, null, null, null, null, null, null, null, null, null);
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
