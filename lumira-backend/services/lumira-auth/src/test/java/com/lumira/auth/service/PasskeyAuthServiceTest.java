package com.lumira.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.auth.PasskeyAuthenticationCompleteRequest;
import com.lumira.api.auth.PasskeyCredentialRenameRequest;
import com.lumira.api.auth.PasskeyOperationVerificationRequest;
import com.lumira.api.auth.PasskeyRegistrationCompleteRequest;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.PasskeyCredentialAssertionDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasswordLoginVerificationDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyAuthServiceTest {

    @Test
    void passkeyCredentialDtoShouldOnlyExposeSelfServiceSummaryFields() {
        List<String> fields = java.util.Arrays.stream(PasskeyCredentialDTO.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertEquals(List.of(
                "id",
                "label",
                "createdAt",
                "lastUsedAt"
        ), fields);
    }

    @Test
    void listCredentialsShouldRejectUnauthenticatedUserBeforeInternalLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = new PasskeyAuthService(
                systemInternalApi,
                mock(AuthAppService.class),
                securityContextFacade,
                mock(StringRedisTemplate.class),
                new ObjectMapper()
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(1001L, "alice", null, "sid", 1, false, Set.of("*"))
        );

        BizException exception = assertThrows(BizException.class, service::listCredentials);

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).passkeyCredentialDescriptors(any(), any());
    }

    @Test
    void listCredentialsShouldRejectBlankUsernameBeforeInternalLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(1001L, " ", null, "sid", 1, true, Set.of("*"))
        );

        BizException exception = assertThrows(BizException.class, service::listCredentials);

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).passkeyCredentialDescriptors(any(), any());
    }

    @Test
    void listCredentialsShouldRejectMissingSessionVersionBeforeInternalLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(1001L, "alice", null, "sid", null, true, Set.of("*"))
        );

        BizException exception = assertThrows(BizException.class, service::listCredentials);

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).passkeyCredentials(any(), any());
    }

    @Test
    void listCredentialsShouldPassCurrentUserUuidToInternalLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "sid", 1, true, Set.of("*"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.passkeyCredentials(1001L, "user-uuid-1001")).thenReturn(List.of());

        service.listCredentials();

        verify(systemInternalApi).passkeyCredentials(1001L, "user-uuid-1001");
    }

    @Test
    void listCredentialsShouldRejectDisabledTrustedUserBeforeInternalLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedUser(1001L, "alice"));
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(
                new SystemUserSnapshotDTO(1001L, "user-uuid-1001", "alice", null, "DISABLED", null, null, null, null, null, null, null, null, null, null, null)
        );

        BizException exception = assertThrows(BizException.class, service::listCredentials);

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).passkeyCredentials(any(), any());
    }

    @Test
    void renameCredentialShouldRejectInvalidIdBeforeCurrentUserLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));

        BizException exception = assertThrows(BizException.class,
                () -> service.renameCredential(0L, new PasskeyCredentialRenameRequest("Laptop", null, null, null, null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(systemInternalApi, never()).renamePasskeyCredential(any(), any(), any(), any());
        verify(securityContextFacade, never()).getCurrentUser();
    }

    @Test
    void renameCredentialShouldRequireCurrentPasswordBeforeInternalRenameWhenNoRecoveryFactorExists() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));
        CurrentUser currentUser = trustedUser(1001L, "alice");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserProfileById(1001L)).thenReturn(new SystemUserSnapshotDTO(
                1001L, "user-uuid-1001", "alice", "stored-hash", "ENABLED", null, null, null, null, null, null, null, null, null, null, null
        ));
        when(systemInternalApi.listVerificationProviders(1001L, "user-uuid-1001")).thenReturn(List.of());
        when(systemInternalApi.loginCapabilities()).thenReturn(new com.lumira.api.system.LoginCapabilitiesDTO(true, false, false, false, true, true, List.of("password", "passkey")));

        BizException exception = assertThrows(BizException.class,
                () -> service.renameCredential(7L, new PasskeyCredentialRenameRequest("Laptop", null, null, null, null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(systemInternalApi, never()).renamePasskeyCredential(any(), any(), any(), any());
    }

    @Test
    void deleteCredentialShouldRejectInvalidIdBeforeCurrentUserLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));

        BizException exception = assertThrows(BizException.class, () -> service.deleteCredential(-1L, null));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(systemInternalApi, never()).deletePasskeyCredential(any(), any(), any());
        verify(securityContextFacade, never()).getCurrentUser();
    }

    @Test
    void registrationOptionsShouldRejectInvalidChallengeTtlBeforeWritingChallenge() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, redisTemplate);
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "sid", 1, true, Set.of("*"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                1001L, "user-uuid-1001", "alice", "stored-hash", "ENABLED", null, null, null, null, null, null, null, null, null, null, null
        );
        when(systemInternalApi.findUserProfileById(1001L)).thenReturn(user);
        when(systemInternalApi.verifyPasswordLogin("alice", "Password!23")).thenReturn(new PasswordLoginVerificationDTO(user, true, false));
        when(systemInternalApi.passkeySettings()).thenReturn(
                new PasskeySettingsDTO(true, true, true, "example.com", "Lumira", List.of("https://example.com"), 0)
        );

        BizException exception = assertThrows(BizException.class, () -> service.registrationOptions(new PasskeyOperationVerificationRequest("Password!23", null, null, null)));

        assertEquals(ErrorCode.BIZ_ERROR, exception.getErrorCode());
        verify(redisTemplate, never()).opsForValue();
        verify(systemInternalApi, never()).passkeyCredentialDescriptors(any(), any());
    }

    @Test
    void registrationOptionsShouldRequireCurrentPasswordBeforeWritingChallengeWhenNoRecoveryFactorExists() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, redisTemplate);
        CurrentUser currentUser = trustedUser(1001L, "alice");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserProfileById(1001L)).thenReturn(new SystemUserSnapshotDTO(
                1001L, "user-uuid-1001", "alice", "stored-hash", "ENABLED", null, null, null, null, null, null, null, null, null, null, null
        ));
        when(systemInternalApi.listVerificationProviders(1001L, "user-uuid-1001")).thenReturn(List.of());
        when(systemInternalApi.loginCapabilities()).thenReturn(new com.lumira.api.system.LoginCapabilitiesDTO(true, false, false, false, true, true, List.of("password", "passkey")));

        BizException exception = assertThrows(BizException.class, () -> service.registrationOptions(null));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(redisTemplate, never()).opsForValue();
        verify(systemInternalApi, never()).passkeyCredentialDescriptors(any(), any());
    }

    @Test
    void registrationOptionsShouldAcceptVerifiedCurrentPasswordBeforeWritingChallenge() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, redisTemplate);
        CurrentUser currentUser = trustedUser(1001L, "alice");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                1001L, "user-uuid-1001", "alice", "stored-hash", "ENABLED", null, null, null, null, null, null, null, null, null, null, null
        );
        when(systemInternalApi.findUserProfileById(1001L)).thenReturn(user);
        when(systemInternalApi.verifyPasswordLogin("alice", "Password!23")).thenReturn(new PasswordLoginVerificationDTO(user, true, false));
        when(systemInternalApi.passkeySettings()).thenReturn(
                new PasskeySettingsDTO(true, true, true, "example.com", "Lumira", List.of("https://example.com"), 60)
        );
        when(systemInternalApi.passkeyCredentialDescriptors(1001L, "user-uuid-1001")).thenReturn(List.of());

        var options = service.registrationOptions(new PasskeyOperationVerificationRequest("Password!23", null, null, null));

        assertEquals("example.com", ((java.util.Map<?, ?>) options.publicKey().get("rp")).get("id"));
        verify(redisTemplate).opsForValue();
    }

    @Test
    void deleteCredentialShouldRequireCurrentPasswordBeforeInternalDeleteWhenNoRecoveryFactorExists() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, mock(StringRedisTemplate.class));
        CurrentUser currentUser = trustedUser(1001L, "alice");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserProfileById(1001L)).thenReturn(new SystemUserSnapshotDTO(
                1001L, "user-uuid-1001", "alice", "stored-hash", "ENABLED", null, null, null, null, null, null, null, null, null, null, null
        ));
        when(systemInternalApi.listVerificationProviders(1001L, "user-uuid-1001")).thenReturn(List.of());
        when(systemInternalApi.loginCapabilities()).thenReturn(new com.lumira.api.system.LoginCapabilitiesDTO(true, false, false, false, true, true, List.of("password", "passkey")));

        BizException exception = assertThrows(BizException.class, () -> service.deleteCredential(7L, null));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(systemInternalApi, never()).deletePasskeyCredential(any(), any(), any());
    }

    @Test
    void completeAuthenticationShouldRejectBlankChallengeBeforeRedisLookup() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, redisTemplate);

        BizException exception = assertThrows(BizException.class,
                () -> service.completeAuthentication(new PasskeyAuthenticationCompleteRequest(
                        " ",
                        "credential-1",
                        "credential-1",
                        "public-key",
                        new PasskeyAuthenticationCompleteRequest.Response(
                                "client-data",
                                "authenticator-data",
                                "signature",
                                null
                        ),
                        null
                ), mock(HttpServletRequest.class)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(redisTemplate, never()).opsForValue();
        verify(systemInternalApi, never()).passkeySettings();
    }

    @Test
    void completeRegistrationShouldRejectChallengeOwnedByAnotherCurrentUserBeforeSavingCredential() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("lumira:auth:passkey:challenge:challenge-1")).thenReturn("""
                {"type":"registration","challenge":"challenge-1","userId":1001,"userHandle":"handle-a"}
                """);
        CurrentUser currentUser = trustedUser(2002L, "bob");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, redisTemplate);

        BizException exception = assertThrows(BizException.class,
                () -> service.completeRegistration(new PasskeyRegistrationCompleteRequest(
                        "challenge-1",
                        "credential-1",
                        "credential-1",
                        "public-key",
                        new PasskeyRegistrationCompleteRequest.Response("client-data", "attestation-object"),
                        null,
                        List.of("usb"),
                        "Laptop"
                ), mock(HttpServletRequest.class)));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).savePasskeyCredential(any());
    }

    @Test
    void completeRegistrationShouldRejectChallengeWithoutTrustedSessionSnapshotBeforeSavingCredential() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("lumira:auth:passkey:challenge:challenge-1")).thenReturn("""
                {"type":"registration","challenge":"challenge-1","userId":1001,"userHandle":"handle-a","userUuid":"user-uuid-1001","sessionId":"sid-other","sessionVersion":1,"permissionsVersion":"permissions-1"}
                """);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedUser(1001L, "alice"));
        PasskeyAuthService service = service(systemInternalApi, securityContextFacade, redisTemplate);

        BizException exception = assertThrows(BizException.class,
                () -> service.completeRegistration(new PasskeyRegistrationCompleteRequest(
                        "challenge-1",
                        "credential-1",
                        "credential-1",
                        "public-key",
                        new PasskeyRegistrationCompleteRequest.Response("client-data", "attestation-object"),
                        null,
                        List.of("usb"),
                        "Laptop"
                ), mock(HttpServletRequest.class)));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).savePasskeyCredential(any());
    }

    @Test
    void completeAuthenticationShouldRejectMismatchedUserHandleBeforeUpdatingUsage() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        AuthAppService authAppService = mock(AuthAppService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("lumira:auth:passkey:challenge:challenge-1")).thenReturn("""
                {"type":"authentication","challenge":"challenge-1","userId":null,"userHandle":null}
                """);
        when(systemInternalApi.passkeySettings()).thenReturn(
                new PasskeySettingsDTO(true, true, true, "example.com", "Lumira", List.of("https://example.com"), 60)
        );
        when(systemInternalApi.passkeyCredentialAssertion("credential-1")).thenReturn(new PasskeyCredentialAssertionDTO(
                10L,
                1001L,
                "user-uuid-1001",
                "handle-a",
                "credential-1",
                "public-key",
                0L
        ));
        PasskeyAuthService service = new PasskeyAuthService(
                systemInternalApi,
                authAppService,
                securityContextFacade,
                redisTemplate,
                new ObjectMapper()
        );

        BizException exception = assertThrows(BizException.class,
                () -> service.completeAuthentication(new PasskeyAuthenticationCompleteRequest(
                        "challenge-1",
                        "credential-1",
                        "credential-1",
                        "public-key",
                        new PasskeyAuthenticationCompleteRequest.Response(
                                base64Url("{\"type\":\"webauthn.get\",\"challenge\":\"challenge-1\",\"origin\":\"https://example.com\"}".getBytes(StandardCharsets.UTF_8)),
                                base64Url(validAuthenticatorData("example.com")),
                                base64Url("signature".getBytes(StandardCharsets.UTF_8)),
                                "handle-b"
                        ),
                        null
                ), mock(HttpServletRequest.class)));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).updatePasskeyCredentialUsage(any());
        verify(authAppService, never()).loginVerifiedUser(any(), any(), any());
    }

    private PasskeyAuthService service(
            SystemInternalApi systemInternalApi,
            SecurityContextFacade securityContextFacade,
            StringRedisTemplate redisTemplate
    ) {
        when(systemInternalApi.findUserIdentityById(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0, Long.class);
            return new SystemUserSnapshotDTO(
                    userId,
                    "user-uuid-" + userId,
                    "alice",
                    null,
                    "ENABLED",
                    null, null, null, null, null, null, null, null, null, null, null
            );
        });
        when(systemInternalApi.permissionSnapshot(anyLong(), anyString())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0, Long.class);
            return new PermissionSnapshotDTO(
                    "permissions-" + userId,
                    List.of("*"),
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    "/"
            );
        });
        return new PasskeyAuthService(
                systemInternalApi,
                mock(AuthAppService.class),
                securityContextFacade,
                redisTemplate,
            new ObjectMapper()
        );
    }

    private static byte[] validAuthenticatorData(String rpId) throws Exception {
        byte[] authData = new byte[37];
        System.arraycopy(MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8)), 0, authData, 0, 32);
        authData[32] = 0x05;
        return authData;
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static CurrentUser trustedUser(Long userId, String username) {
        CurrentUser currentUser = new CurrentUser(userId, username, null, "sid-" + userId, 1, true, Set.of("*"));
        currentUser.setUserUuid("user-uuid-" + userId);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
