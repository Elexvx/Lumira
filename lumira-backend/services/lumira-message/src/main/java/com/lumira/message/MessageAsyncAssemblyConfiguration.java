package com.lumira.message;

import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.message.app.PlatformEventOutboxService;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.controller.InternalJobController;
import com.lumira.message.mapper.MessagePlatformEventOutboxMapper;
import com.lumira.message.service.MessageEventDeliveryService;
import com.lumira.message.service.MessageEventFactory;
import com.lumira.message.service.MessageRecipientResolver;
import com.lumira.message.service.MessageWebSocketRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLumiraAsyncEnabled
@EnableConfigurationProperties(MessageProperties.class)
@MapperScan(
        basePackageClasses = MessagePlatformEventOutboxMapper.class,
        annotationClass = Mapper.class
)
@Import({
        PlatformEventOutboxService.class,
        MessageEventFactory.class,
        MessageWebSocketRegistry.class,
        MessageRecipientResolver.class,
        MessageEventDeliveryService.class,
        InternalJobController.class
})
public class MessageAsyncAssemblyConfiguration {
}
