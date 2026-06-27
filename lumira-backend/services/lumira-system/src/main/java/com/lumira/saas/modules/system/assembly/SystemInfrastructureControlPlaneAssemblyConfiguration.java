package com.lumira.saas.modules.system.assembly;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.config.JacksonCompatibilityConfig;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxService;
import com.lumira.saas.infrastructure.event.PlatformEventProperties;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.domain.SystemDomainEventPublisher;
import com.lumira.saas.infrastructure.pagination.KeysetCursorCodec;
import com.lumira.saas.infrastructure.persistence.BatchJdbcHelper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.infrastructure.redis.RedisConfig;
import com.lumira.saas.infrastructure.redis.RedisStartupCleanupProperties;
import com.lumira.saas.infrastructure.redis.RedisStartupCleanupRunner;
import com.lumira.saas.infrastructure.security.FieldEncryptionMigrationRunner;
import com.lumira.saas.infrastructure.security.JwtAuthFilter;
import com.lumira.saas.infrastructure.security.RuntimeSecurityPropertiesValidator;
import com.lumira.saas.infrastructure.security.SecurityConfig;
import com.lumira.saas.infrastructure.security.SecurityProperties;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.CaptchaService;
import com.lumira.saas.infrastructure.security.service.InitialPasswordChangeGuard;
import com.lumira.saas.infrastructure.security.service.JwtTokenService;
import com.lumira.saas.infrastructure.security.service.LoginProtectionService;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties({
        PlatformEventProperties.class,
        RedisStartupCleanupProperties.class,
        SecurityProperties.class
})
@MapperScan(
        basePackages = {
                "com.lumira.saas.infrastructure.event",
                "com.lumira.saas.infrastructure.persistence.mybatis"
        },
        annotationClass = Mapper.class
)
@Import({
        AuthSessionStore.class,
        BatchJdbcHelper.class,
        CacheTemplate.class,
        CaptchaService.class,
        FieldEncryptionMigrationRunner.class,
        InitialPasswordChangeGuard.class,
        JacksonCompatibilityConfig.class,
        JwtAuthFilter.class,
        JwtTokenService.class,
        KeysetCursorCodec.class,
        LoginProtectionService.class,
        MyBatisQueryOperations.class,
        PasswordPolicyService.class,
        PlatformEventProperties.class,
        PlatformEventOutboxService.class,
        PlatformEventPublisher.class,
        ReadModelVersionService.class,
        RedisConfig.class,
        RedisStartupCleanupRunner.class,
        RuntimeSecurityPropertiesValidator.class,
        SecurityConfig.class,
        SecuritySettingsService.class,
        SessionAuthenticationService.class,
        SystemDomainEventPublisher.class
})
public class SystemInfrastructureControlPlaneAssemblyConfiguration {
}
