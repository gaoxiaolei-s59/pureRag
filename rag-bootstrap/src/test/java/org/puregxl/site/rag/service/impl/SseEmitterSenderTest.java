package org.puregxl.site.rag.service.impl;

import org.junit.jupiter.api.Test;
import org.puregxl.site.framework.web.SseEmitterSender;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterSenderTest {

    @Test
    void failCompletesSseWithoutTriggeringMvcErrorDispatch() {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.fail(new IllegalStateException("模型流式调用失败"));

        assertThat(emitter.completed.get()).isTrue();
        assertThat(emitter.completedWithError.get()).isFalse();
    }

    private static final class RecordingSseEmitter extends SseEmitter {
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicBoolean completedWithError = new AtomicBoolean(false);

        @Override
        public void complete() {
            completed.set(true);
        }

        @Override
        public void completeWithError(Throwable ex) {
            completedWithError.set(true);
        }

        @Override
        public void send(Object object) throws IOException {
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
        }
    }
}
