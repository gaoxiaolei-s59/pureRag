package org.puregxl.site.bootstrap.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MilvusClientConfiguration {

    private final RagVectorProperties ragVectorProperties;

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClientV2() {
        return new MilvusClientV2(ConnectConfig.builder()
                .uri(ragVectorProperties.getMilvus().getUri())
                .build());
    }
}
