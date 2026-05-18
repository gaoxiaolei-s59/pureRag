package org.puregxl.site.bootstrap.config;

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
     * RustFS 控制台地址，用于生成前端可浏览的文件链接。
     */
    @NotBlank
    private String consoleUrl = "http://localhost:9001/rustfs/console";

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
