package org.puregxl.site.rag.service.impl;

import org.junit.jupiter.api.Test;
import org.puregxl.site.rag.pipeline.ChatPipeLine;
import org.puregxl.site.rag.pipeline.StreamChatContext;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceImplTest {

    @Test
    void streamChatBuildsContextAndRegistersTaskForStop() {
        ChatPipeLine chatPipeLine = mock(ChatPipeLine.class);
        RagChatServiceImpl service = new RagChatServiceImpl(chatPipeLine);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        StreamCancellationHandle handle = () -> cancelled.set(true);

        when(chatPipeLine.execute(any(StreamChatContext.class))).thenReturn(handle);

        service.streamChat("什么是 RAG？", "conv-1", true, new SseEmitter(1000L));

        var contextCaptor = org.mockito.ArgumentCaptor.forClass(StreamChatContext.class);
        verify(chatPipeLine).execute(contextCaptor.capture());
        StreamChatContext context = contextCaptor.getValue();
        assertThat(context.getQuestion()).isEqualTo("什么是 RAG？");
        assertThat(context.getConversationId()).isEqualTo("conv-1");
        assertThat(context.isDeepThinking()).isTrue();
        assertThat(context.getTaskId()).isNotBlank();
        assertThat(context.getCallback()).isNotNull();

        service.stopTask(context.getTaskId());

        assertThat(cancelled).isTrue();
    }
}
