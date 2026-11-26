package com.Proyect.Ecommerce.security;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Every instance hears when a session is replaced, so it can close its own streams for it. */
@Configuration
public class SessionEventsConfig {

    @Bean
    public RedisMessageListenerContainer sessionEvents(RedisConnectionFactory redis, SessionService sessions) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis);
        container.addMessageListener(
                (message, pattern) -> sessions.replaced(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(SessionService.CHANNEL));
        return container;
    }
}
