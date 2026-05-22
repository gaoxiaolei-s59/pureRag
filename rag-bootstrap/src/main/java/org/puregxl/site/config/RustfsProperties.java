package org.puregxl.site.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * RustFS/S3 兼容对象存储配置。
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "rustfs")
public class RustfsProperties {

    /**
     * RustFS 服务地址。
     */
    @NotBlank
    private String url = "http://localhost:9000";

    /**
     * Access Key。
     */
    @NotBlank
    private String accessKeyId = "rustfsadmin";

    /**
     * Secret Key。
     */
    @NotBlank
    private String secretAccessKey = "rustfsadmin";

    /**
     * 启动时桶不存在是否自动创建。
     */
    private Boolean createBucketIfMissing = true;
}
