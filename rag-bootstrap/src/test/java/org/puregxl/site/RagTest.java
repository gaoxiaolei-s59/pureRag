package org.puregxl.site;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.chat.LLMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class RagTest {
    @Autowired
    public LLMService llmService;

    @Test
    public void test() {
        System.out.println(llmService);
    }
}
