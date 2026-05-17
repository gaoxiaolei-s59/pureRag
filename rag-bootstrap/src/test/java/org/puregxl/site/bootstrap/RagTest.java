package org.puregxl.site.bootstrap;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.chat.LLMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RagTest {
    @Autowired
    public LLMService llmService;

    @Test
    public void test() {
        System.out.println(llmService);
    }
}
