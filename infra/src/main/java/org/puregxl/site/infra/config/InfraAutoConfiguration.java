package org.puregxl.site.infra.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Infra 模块自动装配入口。
 *
 * <p>业务模块只要引入 infra 依赖，即可自动注册模型路由、聊天、向量、重排和 token 相关组件。
 */
@AutoConfiguration
@EnableConfigurationProperties(AIModelProperties.class)
@ComponentScan(
        basePackages = "org.puregxl.site.infra",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = SpringBootConfiguration.class
        )
)
public class InfraAutoConfiguration {

    @Bean("syncHttpClient")
    @ConditionalOnMissingBean(OkHttpClient.class)
    public OkHttpClient syncHttpClient() {
        return new OkHttpClient();
    }

    @Bean("modelStreamExecutor")
    @ConditionalOnMissingBean(name = "modelStreamExecutor")
    public ExecutorService modelStreamExecutor() {
        return Executors.newCachedThreadPool();
    }
}
