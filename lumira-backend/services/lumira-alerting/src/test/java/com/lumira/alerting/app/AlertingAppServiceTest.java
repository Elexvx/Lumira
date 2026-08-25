package com.lumira.alerting.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.alerting.infrastructure.AlertDeliveryGateway;
import com.lumira.alerting.infrastructure.AlertingRepository;
import com.lumira.alerting.infrastructure.AlertingSecretCrypto;
import com.lumira.alerting.model.AlertingModels;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlertingAppServiceTest {
    @Test
    void rejectsRulesOutsideControlledSignalCatalog() {
        AlertingRepository repository = mock(AlertingRepository.class);
        when(repository.pluginEnabled()).thenReturn(true);
        AlertingAppService service = new AlertingAppService(
                repository, mock(AlertingSecretCrypto.class), mock(AlertDeliveryGateway.class));
        AlertingModels.RuleRequest request = new AlertingModels.RuleRequest(
                "任意脚本", "PROMETHEUS", "evil.custom.query", "GT", BigDecimal.ONE,
                300, 60, "CRITICAL", 1L, true, Map.of(), null
        );

        assertThatThrownBy(() -> service.previewRule(request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("controlled alert catalog");
    }

    @Test
    void directorySyncMatchesEmailFirstAndLeavesAmbiguousUsersUnmatched() {
        AlertingRepository repository = mock(AlertingRepository.class);
        AlertingSecretCrypto crypto = mock(AlertingSecretCrypto.class);
        AlertDeliveryGateway gateway = mock(AlertDeliveryGateway.class);
        AlertingAppService service = new AlertingAppService(repository, crypto, gateway);
        AlertingRepository.ChannelRecord channel = new AlertingRepository.ChannelRecord(
                5, "飞书应用", "FEISHU_APP", true, "encrypted", "fp", null, null, null, 1, LocalDateTime.now());
        when(repository.pluginEnabled()).thenReturn(true);
        when(repository.findChannel(5)).thenReturn(Optional.of(channel));
        when(crypto.decrypt("encrypted")).thenReturn(Map.of("appId", "id", "appSecret", "secret"));
        when(gateway.directoryUsers(eq("FEISHU_APP"), eq(Map.of("appId", "id", "appSecret", "secret"))))
                .thenReturn(List.of(
                        new AlertDeliveryGateway.ExternalDirectoryUser("u-email", "邮箱匹配", "ONE@example.com", "13800000001"),
                        new AlertDeliveryGateway.ExternalDirectoryUser("u-phone-a", "手机歧义 A", null, "+86 13900000000"),
                        new AlertDeliveryGateway.ExternalDirectoryUser("u-phone-b", "手机歧义 B", null, "13900000000")
                ));
        when(repository.localDirectoryUsers()).thenReturn(List.of(
                new AlertingRepository.LocalDirectoryUser(1, "uuid-1", "用户一", "one@example.com", "13900000000"),
                new AlertingRepository.LocalDirectoryUser(2, "uuid-2", "用户二", null, "13900000000")
        ));
        CurrentUser operator = new CurrentUser(1001L, "admin", "s", 1, true, Set.of());

        Map<String, Object> result = service.syncDirectory(5, operator);

        assertThat(result).containsEntry("matched", 1).containsEntry("ambiguous", 1).containsEntry("unmatched", 0);
        verify(repository).replaceAutomaticDirectoryMappings(eq(5L), anyList(), eq(1001L));
    }

    @Test
    void disabledPluginRejectsConfigurationAccess() {
        AlertingRepository repository = mock(AlertingRepository.class);
        when(repository.pluginEnabled()).thenReturn(false);
        AlertingAppService service = new AlertingAppService(
                repository, mock(AlertingSecretCrypto.class), mock(AlertDeliveryGateway.class));

        assertThatThrownBy(service::catalog)
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.PLUGIN_NOT_ENABLED));
    }
}
