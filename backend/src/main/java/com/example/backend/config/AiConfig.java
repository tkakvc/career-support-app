package com.example.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("suggestions");
        // 学習提案の結果を24時間キャッシュする
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS));
        return manager;
    }
}
