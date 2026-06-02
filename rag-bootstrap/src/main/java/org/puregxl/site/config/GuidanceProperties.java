package org.puregxl.site.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 意图澄清配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.guidance")
public class GuidanceProperties {
}
