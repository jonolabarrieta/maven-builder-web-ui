package net.olaba.mvnbuilder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

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
        // When a bulk action fills the pool, run the next task on the submitting
        // thread. This applies backpressure instead of rejecting the action.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("mvn-builder-");
        executor.initialize();
        return executor;
    }
}
