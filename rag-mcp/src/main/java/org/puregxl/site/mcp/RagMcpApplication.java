package org.puregxl.site.mcp;

import org.mybatis.spring.annotation.MapperScan;
import org.puregxl.site.mcp.service.RagToolService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@MapperScan("org.puregxl.site.mcp.dao.mapper")
@SpringBootApplication
public class RagMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider ragToolCallbackProvider(RagToolService ragToolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ragToolService)
                .build();
    }
}
