package com.yupi.springbootinit.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "spring.redis")
@Configuration
public class RedissonConfig {
    private Integer database;
    private String host;
    private Integer port;

    @Bean
    public RedissonClient getRedissonClient() {
        Config config = new Config();

        config.useSingleServer()
                .setDatabase(database)
                .setAddress("redis://" + host + ":" + port);

        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }
}
