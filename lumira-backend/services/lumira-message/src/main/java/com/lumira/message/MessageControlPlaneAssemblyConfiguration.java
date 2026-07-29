package com.lumira.message;

import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.OperationAuditService;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.config.MessageWebSocketConfig;
import com.lumira.message.controller.MessageController;
import com.lumira.message.controller.MessageReadinessV2Controller;
import com.lumira.message.controller.MessageV2Controller;
import com.lumira.message.event.MessageEventConsumptionGuard;
import com.lumira.message.event.ReviewResultEventStreamConsumer;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import com.lumira.message.infrastructure.security.MessageJwtAuthFilter;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import com.lumira.message.infrastructure.security.SecurityProperties;
import com.lumira.message.mapper.MessageNoticeMapper;
import com.lumira.message.service.MessageConnectionSnapshotService;
import com.lumira.message.service.MessageEventDeliveryService;
import com.lumira.message.service.MessageEventFactory;
import com.lumira.message.service.MessagePushService;
import com.lumira.message.service.MessageRecipientResolver;
import com.lumira.message.service.MessageSessionHandshakeInterceptor;
import com.lumira.message.service.MessageWebSocketHandler;
import com.lumira.message.service.MessageWebSocketRegistry;
import com.lumira.message.service.MessageWebSocketTicketService;
import com.lumira.message.service.SmtpNotificationMailService;
import com.lumira.message.service.WechatOfficialAccountNotificationService;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraControlPlaneEnabled
@EnableConfigurationProperties({
        MessageProperties.class,
        SecurityProperties.class
})
@MapperScan(
        basePackageClasses = MessageNoticeMapper.class,
        annotationClass = Mapper.class
)
@Import({
        MessageWebSocketConfig.class,
        MessageController.class,
        MessageReadinessV2Controller.class,
        MessageV2Controller.class,
        MessageAppService.class,
        OperationAuditService.class,
        com.lumira.message.app.PlatformEventOutboxService.class,
        CacheTemplate.class,
        com.lumira.message.infrastructure.security.JwtTokenService.class,
        MessageJwtAuthFilter.class,
        MessageSessionAuthenticationService.class,
        WechatOfficialAccountNotificationService.class,
        SmtpNotificationMailService.class,
        MessageWebSocketTicketService.class,
        MessageWebSocketRegistry.class,
        MessageWebSocketHandler.class,
        MessageSessionHandshakeInterceptor.class,
        MessageRecipientResolver.class,
        MessagePushService.class,
        MessageEventFactory.class,
        MessageEventDeliveryService.class,
        MessageConnectionSnapshotService.class,
        MessageEventConsumptionGuard.class,
        ReviewResultEventStreamConsumer.class
})
public class MessageControlPlaneAssemblyConfiguration {
}
