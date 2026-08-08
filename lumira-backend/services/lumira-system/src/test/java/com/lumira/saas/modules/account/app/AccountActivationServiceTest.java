package com.lumira.saas.modules.account.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.api.expert.ExpertAccountActivationPort;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.modules.account.infrastructure.JdbcAccountActivationRepository;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.account.vo.AccountActivationVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountActivationServiceTest {

    @Test
    void createActivationTokenShouldRejectMissingOperatorBeforeDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        assertThatThrownBy(() -> service.createActivationToken(9001L, 1001L, null, "operator-uuid-42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted account activation operator");

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void createActivationTokenShouldRejectInvalidUserBeforeDatabaseWrite() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        assertThatThrownBy(() -> service.createActivationToken(0L, 1001L, 42L, "operator-uuid-42"))
                .hasMessageContaining("activation user is required");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void verifyShouldRejectMalformedTokenBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        AccountActivationVO.TokenInfo info = service.verify("x".repeat(1024));

        assertThat(info.isValid()).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createActivationTokenShouldRequireUserUuidBeforeTokenInsert() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(9001L))).thenReturn("user-uuid-9001");
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(42L))).thenReturn("operator-uuid-42");
        when(jdbcTemplate.queryForObject(contains("select status from sys_user"), eq(String.class), eq(42L), eq("operator-uuid-42"))).thenReturn("ENABLED");
        when(jdbcTemplate.update(
                contains("insert into sys_account_activation_token"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(9001L),
                eq("user-uuid-9001"),
                eq(1001L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(42L),
                eq("operator-uuid-42"),
                eq(42L),
                eq("operator-uuid-42")
        )).thenReturn(1);
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        String token = service.createActivationToken(9001L, 1001L, 42L, "operator-uuid-42");

        assertThat(token).hasSize(43);
        verify(jdbcTemplate).update(
                contains("where user_id = ? and user_uuid = ?"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(42L),
                eq("operator-uuid-42"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(9001L),
                eq("user-uuid-9001")
        );
        verify(jdbcTemplate).update(
                contains("token_hash, user_id, user_uuid, expert_id"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(9001L),
                eq("user-uuid-9001"),
                eq(1001L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(42L),
                eq("operator-uuid-42"),
                eq(42L),
                eq("operator-uuid-42")
        );
    }

    @Test
    void createActivationTokenShouldRejectWhenTokenInsertMisses() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(9001L))).thenReturn("user-uuid-9001");
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(42L))).thenReturn("operator-uuid-42");
        when(jdbcTemplate.queryForObject(contains("select status from sys_user"), eq(String.class), eq(42L), eq("operator-uuid-42"))).thenReturn("ENABLED");
        when(jdbcTemplate.update(
                contains("insert into sys_account_activation_token"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(9001L),
                eq("user-uuid-9001"),
                eq(1001L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(42L),
                eq("operator-uuid-42"),
                eq(42L),
                eq("operator-uuid-42")
        )).thenReturn(0);
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        assertThatThrownBy(() -> service.createActivationToken(9001L, 1001L, 42L, "operator-uuid-42"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Activation token changed, please retry");
                });
    }

    @Test
    void createActivationTokenShouldRejectMismatchedOperatorUuidBeforeTokenInsert() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(9001L))).thenReturn("user-uuid-9001");
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(42L))).thenReturn("operator-uuid-42");
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        assertThatThrownBy(() -> service.createActivationToken(9001L, 1001L, 42L, "other-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator identity mismatch");

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void createActivationTokenShouldRejectDisabledOperatorBeforeTokenInsert() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(9001L))).thenReturn("user-uuid-9001");
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(42L))).thenReturn("operator-uuid-42");
        when(jdbcTemplate.queryForObject(contains("select status from sys_user"), eq(String.class), eq(42L), eq("operator-uuid-42"))).thenReturn("DISABLED");
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        assertThatThrownBy(() -> service.createActivationToken(9001L, 1001L, 42L, "operator-uuid-42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator is disabled");

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void createActivationTokenShouldRejectOperatorWithoutTrustedStatusBeforeTokenInsert() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(9001L))).thenReturn("user-uuid-9001");
        when(jdbcTemplate.queryForObject(contains("select uuid from sys_user"), eq(String.class), eq(42L))).thenReturn("operator-uuid-42");
        when(jdbcTemplate.queryForObject(contains("select status from sys_user"), eq(String.class), eq(42L), eq("operator-uuid-42"))).thenReturn(" ");
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        assertThatThrownBy(() -> service.createActivationToken(9001L, 1001L, 42L, "operator-uuid-42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator is disabled");

        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void verifyShouldBindTokenUserByUuid() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        when(jdbcTemplate.query(
                contains("u.uuid = t.user_uuid"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(List.of());
        AccountActivationService service = service(jdbcTemplate, mock(PasswordPolicyService.class), mock(IamUserService.class));

        AccountActivationVO.TokenInfo info = service.verify("A".repeat(43));

        assertThat(info.isValid()).isFalse();
        verify(jdbcTemplate).query(
                contains("u.uuid = t.user_uuid"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }

    @Test
    void completeShouldRejectMalformedTokenBeforePasswordPolicyOrDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        IamUserService iamUserService = mock(IamUserService.class);
        AccountActivationService service = service(jdbcTemplate, passwordPolicyService, iamUserService);

        assertThatThrownBy(() -> service.complete("x".repeat(1024), "Weak"))
                .hasMessageContaining("Token is invalid");

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(passwordPolicyService);
        verifyNoInteractions(iamUserService);
    }

    @Test
    void completeShouldConsumeTokenWithHashAndUserUuidBoundary() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        IamUserService iamUserService = mock(IamUserService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        ExpertAccountActivationPort expertAccountActivationPort = mock(ExpertAccountActivationPort.class);
        when(passwordEncoder.encode("StrongerPassword1!")).thenReturn("encoded-password");
        when(jdbcTemplate.update(
                contains("update sys_account_activation_token"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(1);
        when(jdbcTemplate.update(
                contains("update sys_user"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(1);
        when(expertAccountActivationPort.activate(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(jdbcTemplate.query(
                contains("t.token_hash = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            String tokenHash = invocation.getArgument(2);
            return List.of(mapper.mapRow(new SqlRow(Map.of(
                    "id", 501L,
                    "tokenHash", tokenHash,
                    "userId", 9001L,
                    "userUuid", "user-uuid-9001",
                    "expertId", 1001L,
                    "username", "expert",
                    "email", "expert@example.com"
            )), 0));
        });
        AccountActivationService service = new AccountActivationService(
                new JdbcAccountActivationRepository(jdbcTemplate),
                passwordEncoder,
                passwordPolicyService,
                iamUserService,
                mock(SmtpMailService.class),
                expertAccountActivationPort
        );

        boolean completed = service.complete("A".repeat(43), "StrongerPassword1!");

        assertThat(completed).isTrue();
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(jdbcTemplate, iamUserService, expertAccountActivationPort);
        inOrder.verify(jdbcTemplate).update(
                contains("update sys_account_activation_token"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        );
        inOrder.verify(jdbcTemplate).update(
                contains("update sys_user"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        );
        inOrder.verify(iamUserService).upsertPasswordCredential(9001L, "user-uuid-9001", "encoded-password");
        verify(jdbcTemplate).update(
                contains("and token_hash = ?"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(9001L),
                eq("user-uuid-9001"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(501L),
                org.mockito.ArgumentMatchers.anyString(),
                eq(9001L),
                eq("user-uuid-9001")
        );
        verify(jdbcTemplate).update(
                contains("from sys_account_activation_token t"),
                eq("encoded-password"),
                eq(9001L),
                eq("user-uuid-9001"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                eq(9001L),
                eq("user-uuid-9001"),
                eq(501L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
        verify(expertAccountActivationPort).activate(org.mockito.ArgumentMatchers.argThat(activation ->
                activation.expertId().equals(1001L)
                        && activation.userId().equals(9001L)
                        && activation.userUuid().equals("user-uuid-9001")));
        verify(iamUserService).upsertPasswordCredential(9001L, "user-uuid-9001", "encoded-password");
    }

    @Test
    void completeShouldNotWritePasswordWhenTokenWasAlreadyConsumedConcurrently() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        IamUserService iamUserService = mock(IamUserService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("StrongerPassword1!")).thenReturn("encoded-password");
        when(jdbcTemplate.query(
                contains("t.token_hash = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            String tokenHash = invocation.getArgument(2);
            return List.of(mapper.mapRow(new SqlRow(Map.of(
                    "id", 501L,
                    "tokenHash", tokenHash,
                    "userId", 9001L,
                    "userUuid", "user-uuid-9001",
                    "expertId", 1001L,
                    "username", "expert",
                    "email", "expert@example.com"
            )), 0));
        });
        when(jdbcTemplate.update(
                contains("update sys_account_activation_token"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(0);
        AccountActivationService service = new AccountActivationService(
                new JdbcAccountActivationRepository(jdbcTemplate),
                passwordEncoder,
                passwordPolicyService,
                iamUserService,
                mock(SmtpMailService.class)
        );

        assertThatThrownBy(() -> service.complete("A".repeat(43), "StrongerPassword1!"))
                .hasMessageContaining("Token is invalid");

        verify(jdbcTemplate, never()).update(contains("update sys_user"), org.mockito.ArgumentMatchers.any(Object[].class));
        verifyNoInteractions(iamUserService);
    }

    @Test
    void completeShouldNotWriteIamCredentialWhenActivationUserChangedConcurrently() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        IamUserService iamUserService = mock(IamUserService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("StrongerPassword1!")).thenReturn("encoded-password");
        when(jdbcTemplate.query(
                contains("t.token_hash = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            String tokenHash = invocation.getArgument(2);
            return List.of(mapper.mapRow(new SqlRow(Map.of(
                    "id", 501L,
                    "tokenHash", tokenHash,
                    "userId", 9001L,
                    "userUuid", "user-uuid-9001",
                    "expertId", 1001L,
                    "username", "expert",
                    "email", "expert@example.com"
            )), 0));
        });
        when(jdbcTemplate.update(
                contains("update sys_account_activation_token"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(1);
        when(jdbcTemplate.update(
                contains("update sys_user"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(0);
        AccountActivationService service = new AccountActivationService(
                new JdbcAccountActivationRepository(jdbcTemplate),
                passwordEncoder,
                passwordPolicyService,
                iamUserService,
                mock(SmtpMailService.class)
        );

        assertThatThrownBy(() -> service.complete("A".repeat(43), "StrongerPassword1!"))
                .hasMessageContaining("Activation user changed");

        verifyNoInteractions(iamUserService);
    }

    @Test
    void completeShouldRejectWhenActivationExpertChangedConcurrently() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        IamUserService iamUserService = mock(IamUserService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        ExpertAccountActivationPort expertAccountActivationPort = mock(ExpertAccountActivationPort.class);
        when(passwordEncoder.encode("StrongerPassword1!")).thenReturn("encoded-password");
        when(jdbcTemplate.query(
                contains("t.token_hash = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            String tokenHash = invocation.getArgument(2);
            return List.of(mapper.mapRow(new SqlRow(Map.of(
                    "id", 501L,
                    "tokenHash", tokenHash,
                    "userId", 9001L,
                    "userUuid", "user-uuid-9001",
                    "expertId", 1001L,
                    "username", "expert",
                    "email", "expert@example.com"
            )), 0));
        });
        when(jdbcTemplate.update(
                contains("update sys_account_activation_token"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(1);
        when(jdbcTemplate.update(
                contains("update sys_user"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(1);
        when(expertAccountActivationPort.activate(org.mockito.ArgumentMatchers.any())).thenReturn(0);
        AccountActivationService service = new AccountActivationService(
                new JdbcAccountActivationRepository(jdbcTemplate),
                passwordEncoder,
                passwordPolicyService,
                iamUserService,
                mock(SmtpMailService.class),
                expertAccountActivationPort
        );

        assertThatThrownBy(() -> service.complete("A".repeat(43), "StrongerPassword1!"))
                .hasMessageContaining("Activation expert changed");
    }

    private AccountActivationService service(
            MyBatisQueryOperations jdbcTemplate,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService
    ) {
        return new AccountActivationService(
                new JdbcAccountActivationRepository(jdbcTemplate),
                mock(PasswordEncoder.class),
                passwordPolicyService,
                iamUserService,
                mock(SmtpMailService.class)
        );
    }
}
