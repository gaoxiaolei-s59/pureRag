package org.puregxl.site.config;


import cn.hutool.core.thread.ThreadFactoryBuilder;
import jodd.time.TimeUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolExecutorConfig {

    /**
     * CPU核心数
     */
    public static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    @Bean
    public Executor modelStreamExecutor() {
        return new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT / 2),
                Math.max(4, CPU_COUNT),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("model_stream_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    public Executor memoryLoadExecutor() {
        return new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT / 2),
                Math.max(4, CPU_COUNT),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("model_stream_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

}
