package com.lumira.saas.modules.system;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.saas.infrastructure.config.JacksonCompatibilityConfig;
import com.lumira.saas.infrastructure.event.LoggingPlatformEventDispatcher;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxMapper;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxRelay;
import com.lumira.saas.infrastructure.event.PlatformEventOutboxService;
import com.lumira.saas.infrastructure.event.PlatformEventProperties;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.RedisStreamPlatformEventDispatcher;
import com.lumira.saas.infrastructure.event.domain.SystemDomainEventPublisher;
import com.lumira.saas.infrastructure.event.EventConsumptionGuard;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RawSqlMapper;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.redis.RedisConfig;
import com.lumira.saas.infrastructure.redis.RedisStartupCleanupProperties;
import com.lumira.saas.infrastructure.redis.RedisStartupCleanupRunner;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.competition.event.CompetitionPaymentEventHandler;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.audit.mapper.AuditOperationLogMapper;
import com.lumira.saas.modules.audit.infrastructure.MapperOperationAuditRepository;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@EnableConfigurationProperties({
        PlatformEventProperties.class,
        RedisStartupCleanupProperties.class
})
@MapperScan(
        basePackageClasses = {
                AuditOperationLogMapper.class,
                PlatformEventOutboxMapper.class,
                RawSqlMapper.class
        },
        annotationClass = Mapper.class
)
@Import({
        JacksonCompatibilityConfig.class,
        LoggingPlatformEventDispatcher.class,
        MyBatisQueryOperations.class,
        MapperOperationAuditRepository.class,
        OperationAuditService.class,
        OwnerRuntimeMetrics.class,
        PlatformEventOutboxRelay.class,
        PlatformEventOutboxService.class,
        PlatformEventPublisher.class,
        ReadModelVersionService.class,
        RedisConfig.class,
        RedisStartupCleanupRunner.class,
        RedisStreamPlatformEventDispatcher.class,
        SystemDomainEventPublisher.class,
        EventConsumptionGuard.class,
        CompetitionPaymentEventHandler.class,
        com.lumira.saas.infrastructure.job.InternalJobController.class
})
public class SystemAsyncAssemblyConfiguration {
}
