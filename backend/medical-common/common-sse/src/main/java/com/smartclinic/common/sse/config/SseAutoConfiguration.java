package com.smartclinic.common.sse.config;

import com.smartclinic.common.sse.controller.SseController;
import com.smartclinic.common.sse.core.SseEmitterManager;
import com.smartclinic.common.sse.listener.SseTopicListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SSE 自动装配
 *
 * @author Lion Li
 */
@AutoConfiguration
@EnableConfigurationProperties(SseProperties.class)
public class SseAutoConfiguration {

    @Bean
    public SseEmitterManager sseEmitterManager() {
        return new SseEmitterManager();
    }

    @Bean
    @ConditionalOnProperty(value = "sse.enabled", havingValue = "true")
    public SseTopicListener sseTopicListener() {
        return new SseTopicListener();
    }

    @Bean
    @ConditionalOnProperty(value = "sse.enabled", havingValue = "true")
    public SseController sseController(SseEmitterManager sseEmitterManager) {
        return new SseController(sseEmitterManager);
    }

}
