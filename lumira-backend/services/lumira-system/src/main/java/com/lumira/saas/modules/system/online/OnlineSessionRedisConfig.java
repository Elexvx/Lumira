package com.lumira.saas.modules.system.online;

import com.lumira.saas.common.constant.CacheKeyConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "lumira.online-session.redis.enabled", havingValue = "true", matchIfMissing = true)
public class OnlineSessionRedisConfig {

    @Bean
    public ChannelTopic onlineSessionEventTopic() {
        return new ChannelTopic(CacheKeyConstants.onlineSessionEventsChannel());
    }

    @Bean
    public RedisMessageListenerContainer onlineSessionRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            OnlineSessionEventSubscriber onlineSessionEventSubscriber,
            ChannelTopic onlineSessionEventTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(onlineSessionEventSubscriber, onlineSessionEventTopic);
        return container;
    }
}
