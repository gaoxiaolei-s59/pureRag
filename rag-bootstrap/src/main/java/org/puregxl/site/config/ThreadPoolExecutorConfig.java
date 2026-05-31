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

    /**
     * 模型流失输出线程池
     * @return
     */
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

    /**
     * 上下文加载数据库
     * @return
     */
    @Bean
    public Executor memoryLoadExecutor() {
        return new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT / 2),
                Math.max(4, CPU_COUNT),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("memory_load_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }


    /**
     * 意图识别并行执行线程池
     */
    /**
     * 上下文加载数据库
     * @return
     */
    @Bean
    public Executor intentRecognitionExecutor() {
        return new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT / 2),
                Math.max(4, CPU_COUNT),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("intent_recognition_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * 检索上下文并行构建线程池。
     * <p>
     * 这里按子问题粒度并发拆分 KB/MCP 上下文，和意图识别、记忆加载隔离，避免相互抢占同一批线程。
     */
    @Bean
    public Executor retrievalBuildExecutor() {
        return new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT / 2),
                Math.max(4, CPU_COUNT),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("retrieval_build_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * 检索上下文并行构建线程池。
     * <p>
     * 这里按子问题粒度并发拆分 KB/MCP 上下文，和意图识别、记忆加载隔离，避免相互抢占同一批线程。
     */
    @Bean
    public Executor MultiChannelRetrievalexecutor() {
        return new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT / 2),
                Math.max(4, CPU_COUNT),
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                ThreadFactoryBuilder.create()
                        .setNamePrefix("retrieval_build_executor_")
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
