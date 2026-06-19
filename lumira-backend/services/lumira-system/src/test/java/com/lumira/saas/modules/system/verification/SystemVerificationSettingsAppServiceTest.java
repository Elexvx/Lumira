package com.lumira.saas.modules.system.verification;

import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class SystemVerificationSettingsAppServiceTest {

    @Test
    void loadLoginCapabilitiesShouldReuseCachedConfigSnapshots() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured(anyLong())).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.isAvailable(anyLong())).thenReturn(false);
        FieldCryptoService fieldCryptoService = Mockito.mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService
        );

        SystemVO.LoginCapabilitiesVO first = service.loadLoginCapabilities(1001L);
        SystemVO.LoginCapabilitiesVO second = service.loadLoginCapabilities(1001L);

        assertThat(first.getPasswordLoginAvailable()).isTrue();
        assertThat(second.getPasswordLoginAvailable()).isTrue();
        assertThat(queryOperations.queryForListCount.get()).isEqualTo(5);
        assertThat(queryOperations.updateCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldInvalidateCachedConfigSnapshots() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured(anyLong())).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.isAvailable(anyLong())).thenReturn(false);
        FieldCryptoService fieldCryptoService = Mockito.mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService
        );

        SystemVO.LoginCapabilitiesVO before = service.loadLoginCapabilities(1001L);
        assertThat(before.getPasswordLoginAvailable()).isTrue();

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.TRUE);
        request.setEmailLoginEnabled(Boolean.FALSE);
        request.setPasswordLoginEnabled(Boolean.FALSE);
        request.setLoginModeOrder(List.of("password", "sms"));

        SystemVO.VerificationSettingsVO updated = service.updateVerificationSettings(currentUser(), request);

        assertThat(updated.getPasswordLoginEnabled()).isFalse();
        assertThat(queryOperations.updateCount.get()).isGreaterThan(0);
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setCurrentTenantId(1001L);
        currentUser.setUserId(9L);
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        return currentUser;
    }

    private Map<String, String> defaultConfigValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("verification.password-login.enabled", "true");
        values.put("verification.email-login.enabled", "false");
        values.put("verification.totp.enabled", "true");
        values.put("verification.login-mode.order", "password,sms,email,wechat,passkey");
        values.put("verification.sms.enabled", "false");
        values.put("verification.sms.provider", "aliyun");
        values.put("verification.sms.sign-name", "");
        values.put("verification.sms.template-code", "");
        values.put("verification.sms.access-key-id", "");
        values.put("verification.sms.access-key-secret", "");
        values.put("verification.sms.endpoint", "");
        values.put("verification.sms.region", "");
        values.put("verification.passkey.enabled", "false");
        values.put("verification.passkey.passwordless-enabled", "false");
        values.put("verification.passkey.self-binding-enabled", "true");
        values.put("verification.passkey.rp-id", "");
        values.put("verification.passkey.rp-name", "");
        values.put("verification.passkey.allowed-origins", "");
        values.put("verification.passkey.challenge-ttl-seconds", "120");
        return values;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final Map<String, String> values;
        private final AtomicInteger queryForListCount = new AtomicInteger();
        private final AtomicInteger updateCount = new AtomicInteger();

        private RecordingQueryOperations(Map<String, String> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryForListCount.incrementAndGet();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object arg : args) {
                if (!(arg instanceof String key) || !key.contains(".")) {
                    continue;
                }
                if (!values.containsKey(key)) {
                    continue;
                }
                rows.add(Map.of(
                        "configKey", key,
                        "configValue", values.get(key)
                ));
            }
            return rows;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount.incrementAndGet();
            if (args != null && args.length >= 4 && args[1] instanceof String key && args[3] != null) {
                values.put(key, String.valueOf(args[3]));
            }
            return 1;
        }
    }
}
