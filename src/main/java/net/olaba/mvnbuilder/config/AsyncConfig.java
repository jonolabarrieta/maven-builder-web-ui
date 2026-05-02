package net.olaba.mvnbuilder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous task execution.
 */
@Configuration
public class AsyncConfig {

    /**
     * Configures the primary task executor for the application.
     * 
     * @return A ThreadPoolTaskExecutor instance.
     */
    @Bean(name = "taskExecutor")
    @Primary
    public TaskExecutor taskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("mvn-builder-");
        executor.initialize();
        return executor;
    }
}
