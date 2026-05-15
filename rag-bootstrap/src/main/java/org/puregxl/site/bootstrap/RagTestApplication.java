package org.puregxl.site.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("org.puregxl.site.bootstrap.knowledge.dao.mapper")
@SpringBootApplication(scanBasePackages = "org.puregxl.site")
public class RagTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagTestApplication.class, args);
    }
}
