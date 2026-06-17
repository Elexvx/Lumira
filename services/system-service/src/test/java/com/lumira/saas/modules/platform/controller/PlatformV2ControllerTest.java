package com.lumira.saas.modules.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.saas.common.annotation.RepeatSubmit;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PlatformV2ControllerTest {

    @Test
    void platformV2Controller_shouldExposePlatformOwnerManagementAdapter() {
        RequestMapping requestMapping = PlatformV2Controller.class.getAnnotation(RequestMapping.class);

        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/v2/platform");

        Set<String> getEndpoints = methodsWith(GetMapping.class);
        Set<String> postEndpoints = methodsWith(PostMapping.class);
        Set<String> putEndpoints = methodsWith(PutMapping.class);
        Set<String> patchEndpoints = methodsWith(PatchMapping.class);
        Set<String> deleteEndpoints = methodsWith(DeleteMapping.class);

        assertThat(getEndpoints)
                .contains(
                        "publicBootstrap:/public/bootstrap",
                        "configs:/configs",
                        "config:/configs/{id}",
                        "dictTypes:/dict-types",
                        "dictType:/dict-types/{id}",
                        "dictItems:/dict-types/{id}/items",
                        "runtimeAppearanceSettings:/runtime-appearance-settings",
                        "brandingSettings:/branding-settings",
                        "agreementSettings:/agreement-settings",
                        "watermarkSettings:/watermark-settings",
                        "floatingWindowSettings:/floating-window-settings",
                        "securitySettings:/security-settings",
                        "smtpSettings:/smtp-settings",
                        "wechatOfficialAccountSettings:/notification/wechat-official-settings",
                        "auditSummary:/audit/summary",
                        "loginLogs:/audit/login-logs",
                        "operationLogs:/audit/operation-logs",
                        "aiCallLogs:/audit/ai-call-logs",
                        "verificationLogs:/audit/verification-logs",
                        "dashboardSummary:/monitoring/dashboard/summary",
                        "onlineUsers:/monitoring/online-users",
                        "onlineUserEvents:/monitoring/online-users/events"
                );
        assertThat(postEndpoints)
                .contains(
                        "createConfig:/configs",
                        "createDictType:/dict-types",
                        "createDictItem:/dict-types/{id}/items",
                        "testSmtpSettings:/smtp-settings/test"
                );
        assertThat(putEndpoints)
                .contains(
                        "updateConfig:/configs/{id}",
                        "updateDictType:/dict-types/{id}",
                        "updateDictItem:/dict-types/{dictTypeId}/items/{itemId}",
                        "updateBrandingSettings:/branding-settings",
                        "updateAgreementSettings:/agreement-settings",
                        "updateWatermarkSettings:/watermark-settings",
                        "updateFloatingWindowSettings:/floating-window-settings",
                        "updateSecuritySettings:/security-settings",
                        "updateSmtpSettings:/smtp-settings",
                        "updateWechatOfficialAccountSettings:/notification/wechat-official-settings"
                );
        assertThat(patchEndpoints)
                .contains(
                        "banOnlineUser:/monitoring/online-users/{userId}/ban"
                );
        assertThat(deleteEndpoints)
                .contains(
                        "deleteDictType:/dict-types/{id}",
                        "deleteDictItem:/dict-types/{dictTypeId}/items/{itemId}",
                        "resetSmtpSettings:/smtp-settings",
                        "kickOnlineUser:/monitoring/online-users/{sessionId}"
                );
    }

    @Test
    void writeEndpoints_shouldKeepRepeatSubmitProtection() {
        for (Method method : PlatformV2Controller.class.getDeclaredMethods()) {
            if (method.getAnnotation(PostMapping.class) != null
                    || method.getAnnotation(PutMapping.class) != null
                    || method.getAnnotation(PatchMapping.class) != null
                    || method.getAnnotation(DeleteMapping.class) != null) {
                assertThat(method.getAnnotation(RepeatSubmit.class))
                        .as(method.getName())
                        .isNotNull();
            }
        }
    }

    private Set<String> methodsWith(Class<? extends java.lang.annotation.Annotation> annotationClass) {
        return Arrays.stream(PlatformV2Controller.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(annotationClass) != null)
                .map(method -> method.getName() + ":" + String.join(",", values(method.getAnnotation(annotationClass))))
                .collect(Collectors.toSet());
    }

    private String[] values(java.lang.annotation.Annotation annotation) {
        if (annotation instanceof GetMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PostMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PutMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof PatchMapping mapping) {
            return mapping.value();
        }
        if (annotation instanceof DeleteMapping mapping) {
            return mapping.value();
        }
        return new String[0];
    }
}
