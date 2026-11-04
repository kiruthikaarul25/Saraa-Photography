package com.shop.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "previewExecutor")
    public Executor previewExecutor() {
        int cpus = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(Math.max(2, cpus - 1));
        ex.setMaxPoolSize(Math.max(2, cpus - 1));
        ex.setQueueCapacity(50000);          // 10k+ photos queue aagum
        ex.setThreadNamePrefix("preview-");
        ex.initialize();
        return ex;
    }
}