package org.puregxl.site.rag.core.usermemory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class UserMemoryExecutorConfiguration {
    /** 慢模型推理拥有独立线程，队列满时由数据库任务在下一次扫描恢复。 */
    @Bean
    ThreadPoolTaskExecutor userMemoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("user-memory-");
        return executor;
    }
}
