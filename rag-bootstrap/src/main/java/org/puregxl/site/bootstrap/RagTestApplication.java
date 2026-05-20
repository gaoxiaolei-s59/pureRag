package org.puregxl.site.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("org.puregxl.site.bootstrap.knowledge.dao.mapper")
@MapperScan("org.puregxl.site.bootstrap.user.dao.mapper")
@EnableScheduling
@SpringBootApplication
public class RagTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagTestApplication.class, args);
    }
}
