package com.lumira.file.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.file.mapper.FilePlatformEventOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformEventOutboxServiceTest {

    @Test
    void recordShouldRejectInvalidUserIdBeforeInsert() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                0L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L)
        ));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectNonFileSourceBeforeInsert() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                "PLUGIN",
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L)
        ));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectBlankEventTypeBeforeInsert() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                " ",
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L)
        ));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectUntrustedEventKeyBeforeInsert() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED//file.object:3001",
                Map.of("aggregateId", 3001L)
        ));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectOversizedPayloadBeforeInsert() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("body", "x".repeat(256 * 1024))
        ));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void dispatchPendingShouldRejectInvalidLimitBeforeClaiming() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileOutboxDispatcher dispatcher = mock(FileOutboxDispatcher.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, jdbcTemplate);

        assertThrows(IllegalArgumentException.class, () -> service.dispatchPending(dispatcher, 201));

        verify(dispatcher, never()).dispatch(org.mockito.ArgumentMatchers.any());
        verify(jdbcTemplate, never()).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void dispatchRowShouldBeUntrustedWhenHumanUserUuidIsMissing() throws Exception {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxEntity row = outboxRow(3001L);
        row.setPayloadJson("{}");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, jdbcTemplate);
        Method trustCheck = PlatformEventOutboxService.class.getDeclaredMethod("isTrustedDispatchRow", PlatformEventOutboxEntity.class);
        trustCheck.setAccessible(true);

        boolean trusted = (Boolean) trustCheck.invoke(service, row);

        assertThat(trusted).isFalse();
    }

    @Test
    void dispatchRowShouldBeUntrustedWhenPayloadUserUuidDoesNotMatchResolvedUser() throws Exception {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxEntity row = outboxRow(3001L);
        row.setPayloadJson("{\"userUuid\":\"user-uuid-other\"}");
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = systemInternalApiWithUser(2001L, "user-uuid-2001");
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(), mapper, jdbcTemplate, systemInternalApi
        );
        Method trustCheck = PlatformEventOutboxService.class.getDeclaredMethod("isTrustedDispatchRow", PlatformEventOutboxEntity.class);
        trustCheck.setAccessible(true);

        boolean trusted = (Boolean) trustCheck.invoke(service, row);

        assertThat(trusted).isFalse();
    }

    @Test
    void recordShouldPersistSerializedPayload() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = systemInternalApiWithUser(2001L, "user-uuid-2001");
        when(mapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(), mapper, jdbcTemplate, systemInternalApi
        );

        service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L, "userUuid", "user-uuid-2001")
        );

        ArgumentCaptor<PlatformEventOutboxEntity> captor = ArgumentCaptor.forClass(PlatformEventOutboxEntity.class);
        verify(mapper).insert(captor.capture());
        PlatformEventOutboxEntity entity = captor.getValue();
        assertEquals(2001L, entity.getUserId());
        assertEquals("user-uuid-2001", entity.getUserUuid());
        assertEquals(2001L, entity.getCreatedBy());
        assertEquals("user-uuid-2001", entity.getCreatedByUuid());
        assertEquals(2001L, entity.getUpdatedBy());
        assertEquals("user-uuid-2001", entity.getUpdatedByUuid());
        assertEquals(FilePlatformEventTypes.SOURCE_FILE, entity.getSourceType());
        assertEquals(FilePlatformEventTypes.FILE_OBJECT_UPLOADED, entity.getEventType());
        assertEquals("FILE_OBJECT_UPLOADED:file.object:3001", entity.getEventKey());
        assertEquals(PlatformEventOutboxService.STATUS_RECORDED, entity.getDispatchStatus());
        assertTrue(entity.getPayloadJson().contains("\"aggregateId\":3001"));
        assertTrue(entity.getPayloadJson().contains("\"userUuid\":\"user-uuid-2001\""));
    }

    @Test
    void recordShouldRejectWhenInsertMisses() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        when(mapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(0);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalStateException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                null,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L)
        ));
    }

    @Test
    void recordShouldRejectMissingUserUuidWhenUserIdIsPresent() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L)
        ));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectUserUuidMismatchWhenDatabaseCanResolveUser() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = systemInternalApiWithUser(2001L, "user-uuid-2001");
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(), mapper, jdbcTemplate, systemInternalApi
        );

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L, "userUuid", "user-uuid-other")
        ));

        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectUserUuidWhenDatabaseCannotVerifyUser() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(), mapper, jdbcTemplate, systemInternalApi
        );

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L, "userUuid", "user-uuid-2001")
        ));

        verify(systemInternalApi).findTargetUserUuidById(2001L);
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldRejectDisabledUserEvenWhenUserUuidMatches() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PlatformEventOutboxService service = new PlatformEventOutboxService(
                new ObjectMapper(), mapper, jdbcTemplate, systemInternalApi
        );

        assertThrows(IllegalArgumentException.class, () -> service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                2001L,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L, "userUuid", "user-uuid-2001")
        ));

        verify(systemInternalApi).findTargetUserUuidById(2001L);
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(PlatformEventOutboxEntity.class));
    }

    @Test
    void recordShouldNotInventAuditUserForAnonymousFileEvent() {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        when(mapper.insert(any(PlatformEventOutboxEntity.class))).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper);

        service.record(
                FilePlatformEventTypes.SOURCE_FILE,
                FilePlatformEventTypes.FILE_OBJECT_UPLOADED,
                null,
                "FILE_OBJECT_UPLOADED:file.object:3001",
                Map.of("aggregateId", 3001L)
        );

        ArgumentCaptor<PlatformEventOutboxEntity> captor = ArgumentCaptor.forClass(PlatformEventOutboxEntity.class);
        verify(mapper).insert(captor.capture());
        PlatformEventOutboxEntity entity = captor.getValue();
        assertEquals(null, entity.getUserId());
        assertEquals(null, entity.getUserUuid());
        assertEquals(null, entity.getCreatedBy());
        assertEquals(null, entity.getCreatedByUuid());
        assertEquals(null, entity.getUpdatedBy());
        assertEquals(null, entity.getUpdatedByUuid());
    }

    @Test
    void markDeliveredShouldBindRetryCountAndClaimBoundary() throws Exception {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, jdbcTemplate);
        PlatformEventOutboxEntity row = outboxRow(3001L);
        Method markDelivered = PlatformEventOutboxService.class.getDeclaredMethod("markDelivered", PlatformEventOutboxEntity.class);
        markDelivered.setAccessible(true);

        markDelivered.invoke(service, row);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("event_type = ?")
                .contains("event_key = ?")
                .contains("claim_token = ?")
                .contains("user_uuid")
                .contains("retry_count");
        assertThat(argsCaptor.getValue()).contains(0, 0);
    }

    @Test
    void markDeliveredShouldRejectWhenClaimWriteMisses() throws Exception {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, jdbcTemplate);
        PlatformEventOutboxEntity row = outboxRow(3001L);
        Method markDelivered = PlatformEventOutboxService.class.getDeclaredMethod("markDelivered", PlatformEventOutboxEntity.class);
        markDelivered.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> markDelivered.invoke(service, row)
        );

        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("File outbox changed");
    }

    @Test
    void claimForDispatchShouldBindRetryCountBeforeStatusBoundary() throws Exception {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, jdbcTemplate);
        PlatformEventOutboxEntity row = outboxRow(3001L);
        row.setDispatchStatus(PlatformEventOutboxService.STATUS_RECORDED);
        row.setRetryCount(3);
        Method claimForDispatch = PlatformEventOutboxService.class.getDeclaredMethod("claimForDispatch", PlatformEventOutboxEntity.class);
        claimForDispatch.setAccessible(true);

        Object claimed = claimForDispatch.invoke(service, row);

        assertThat(claimed).isEqualTo(Boolean.TRUE);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("retry_count")
                .contains("dispatch_status = ?")
                .contains("claim_expires_at <= ?");
        assertThat(argsCaptor.getValue()).containsSubsequence(
                2001L,
                2001L,
                "user-uuid-2001",
                3,
                3,
                PlatformEventOutboxService.STATUS_RECORDED,
                PlatformEventOutboxService.STATUS_DISPATCHING
        );
    }

    @Test
    void markFailedShouldBindRetryCountAndClaimBoundary() throws Exception {
        FilePlatformEventOutboxMapper mapper = mock(FilePlatformEventOutboxMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        PlatformEventOutboxService service = new PlatformEventOutboxService(new ObjectMapper(), mapper, jdbcTemplate);
        PlatformEventOutboxEntity row = outboxRow(3001L);
        Method markFailed = PlatformEventOutboxService.class.getDeclaredMethod("markFailed", PlatformEventOutboxEntity.class, RuntimeException.class);
        markFailed.setAccessible(true);

        markFailed.invoke(service, row, new RuntimeException("boom"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("event_type = ?")
                .contains("event_key = ?")
                .contains("claim_token = ?")
                .contains("user_uuid")
                .contains("retry_count");
        assertThat(argsCaptor.getValue()).contains(0, 0);
    }

    private PlatformEventOutboxEntity outboxRow(Long id) {
        PlatformEventOutboxEntity row = new PlatformEventOutboxEntity();
        row.setId(id);
        row.setUserId(2001L);
        row.setUserUuid("user-uuid-2001");
        row.setSourceType(FilePlatformEventTypes.SOURCE_FILE);
        row.setEventType(FilePlatformEventTypes.FILE_OBJECT_UPLOADED);
        row.setEventKey("FILE_OBJECT_UPLOADED:file.object:" + id);
        row.setPayloadJson("{\"userUuid\":\"user-uuid-2001\"}");
        row.setDispatchStatus(PlatformEventOutboxService.STATUS_DISPATCHING);
        row.setRetryCount(0);
        row.setClaimToken("claim-" + id);
        row.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        row.setUpdatedBy(2001L);
        row.setUpdatedByUuid("user-uuid-2001");
        row.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        row.setDeleted(0);
        return row;
    }

    private SystemInternalApi systemInternalApiWithUser(Long userId, String userUuid) {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findTargetUserUuidById(userId)).thenReturn(userUuid);
        when(systemInternalApi.findUserIdentityById(userId)).thenReturn(new SystemUserSnapshotDTO(
                userId, userUuid, "test-user", null, "ENABLED", null, null, null,
                null, null, null, null, null, null, null, null
        ));
        return systemInternalApi;
    }
}
