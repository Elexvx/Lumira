package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.ai.app.AiKnowledgeTextExtractor;
import com.lumira.saas.modules.system.app.OnlineSessionManagementAppService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.app.SystemPlatformSettingsAppService;
import com.lumira.saas.modules.system.app.SystemProfileSettingsAppService;
import com.lumira.saas.modules.system.controller.DashboardController;
import com.lumira.saas.modules.system.controller.InternalSystemController;
import com.lumira.saas.modules.system.controller.OnlineSessionController;
import com.lumira.saas.modules.system.controller.ProfileController;
import com.lumira.saas.modules.system.controller.PublicCaptchaController;
import com.lumira.saas.modules.system.controller.PublicSystemController;
import com.lumira.saas.modules.system.controller.SystemController;
import com.lumira.saas.modules.system.controller.SystemDepartmentController;
import com.lumira.saas.modules.system.controller.SystemVerificationController;
import com.lumira.saas.modules.system.department.app.SystemDepartmentAppService;
import com.lumira.saas.modules.system.dict.app.DictRuntimeService;
import com.lumira.saas.modules.system.export.ExcelExportService;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.monitor.app.SystemMonitorAppService;
import com.lumira.saas.modules.system.monitor.controller.SystemMonitorController;
import com.lumira.saas.modules.system.online.OnlineSessionEventPublisher;
import com.lumira.saas.modules.system.online.OnlineSessionEventSubscriber;
import com.lumira.saas.modules.system.online.OnlineSessionRedisConfig;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import com.lumira.saas.modules.system.passkey.PasskeyCredentialAppService;
import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordDictionaryCache;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordDictionaryVersionService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordMetrics;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordPluginStateService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import com.lumira.saas.modules.system.sensitive.controller.SensitiveWordController;
import com.lumira.saas.modules.system.sensitive.security.SensitiveWordFormFilter;
import com.lumira.saas.modules.system.sensitive.security.SensitiveWordRequestBodyAdvice;
import com.lumira.saas.modules.system.support.SmsVerificationSender;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.system.update.app.PlatformUpdateAppService;
import com.lumira.saas.modules.system.update.controller.PlatformUpdateController;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationProperties;
import com.lumira.saas.modules.system.verification.SystemVerificationSettingsAppService;
import com.lumira.saas.modules.system.verification.VerificationDeliveryAuditService;
import com.lumira.saas.modules.system.verification.WechatLoginProperties;
import com.lumira.saas.modules.system.verification.WechatLoginSettingsService;
import com.lumira.saas.modules.system.workorder.app.WorkOrderFeedbackPluginStateService;
import com.lumira.saas.modules.system.workorder.app.WorkOrderFeedbackService;
import com.lumira.saas.modules.system.workorder.controller.WorkOrderFeedbackController;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties({
        SystemVerificationProperties.class,
        WechatLoginProperties.class
})
@MapperScan(
        basePackages = {
                "com.lumira.saas.modules.system.config.mapper",
                "com.lumira.saas.modules.system.export",
                "com.lumira.saas.modules.system.passkey",
                "com.lumira.saas.modules.system.update.mapper"
        },
        annotationClass = Mapper.class
)
@Import({
        AiKnowledgeTextExtractor.class,
        DashboardController.class,
        DictRuntimeService.class,
        ExcelExportService.class,
        ExportTaskService.class,
        InternalSystemController.class,
        OnlineSessionController.class,
        OnlineSessionEventPublisher.class,
        OnlineSessionEventSubscriber.class,
        OnlineSessionManagementAppService.class,
        OnlineSessionRedisConfig.class,
        OnlineSessionStreamService.class,
        PasskeyCredentialAppService.class,
        PlatformUpdateAppService.class,
        PlatformUpdateController.class,
        ProfileController.class,
        PublicCaptchaController.class,
        PublicSystemController.class,
        SensitiveWordController.class,
        SensitiveWordDictionaryCache.class,
        SensitiveWordDictionaryVersionService.class,
        SensitiveWordFormFilter.class,
        SensitiveWordMetrics.class,
        SensitiveWordPluginStateService.class,
        SensitiveWordRequestBodyAdvice.class,
        SensitiveWordService.class,
        SmsVerificationSender.class,
        SmtpMailService.class,
        SystemController.class,
        SystemDepartmentAppService.class,
        SystemDepartmentController.class,
        SystemManagementAppService.class,
        SystemMonitorAppService.class,
        SystemMonitorController.class,
        SystemPlatformSettingsAppService.class,
        SystemPluginViewService.class,
        SystemProfileSettingsAppService.class,
        SystemRoleManagementAppService.class,
        SystemUserManagementAppService.class,
        SystemVerificationAppService.class,
        SystemVerificationController.class,
        SystemVerificationSettingsAppService.class,
        UserExportAppService.class,
        VerificationDeliveryAuditService.class,
        WechatLoginSettingsService.class,
        WorkOrderFeedbackController.class,
        WorkOrderFeedbackPluginStateService.class,
        WorkOrderFeedbackService.class
})
public class SystemOperationsControlPlaneAssemblyConfiguration {
}
