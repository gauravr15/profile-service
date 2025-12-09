package com.odin.profileservice.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serialization for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value serialization
        template.setValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.setHashValueSerializer(new GenericToStringSerializer<>(Object.class));

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CommandLineRunner redisInitializer(RedisTemplate<String, Object> redisTemplate) {
        return args -> {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey("auth:flow:signup"))) {
                redisTemplate.opsForValue().set("auth:flow:signup", "PASSWORD");
            }
            if (!Boolean.TRUE.equals(redisTemplate.hasKey("auth:flow:signin"))) {
                redisTemplate.opsForValue().set("auth:flow:signin", "PASSWORD");
            }
        };
    }
}
