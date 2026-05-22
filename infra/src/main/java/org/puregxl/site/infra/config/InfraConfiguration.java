package org.puregxl.site.infra.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Infra 内部模块配置入口。
 *
 * <p>RagTest 启动类位于 org.puregxl.site 根包下，会直接扫描 infra 模块中的组件。
 * 这里仅负责注册跨模型调用需要共享的基础 Bean 和配置属性，避免再以 starter 自动装配的方式重复扫描模块。
 */
@Configuration
@EnableConfigurationProperties(AIModelProperties.class)
public class InfraConfiguration {

}
