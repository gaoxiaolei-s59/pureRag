package org.puregxl.site.rag.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.rag.pipeline.ChatPipeLine;
import org.puregxl.site.rag.pipeline.StreamChatContext;
import org.puregxl.site.rag.service.MemoryService;
import org.puregxl.site.rag.service.handler.StreamTaskManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceImplTest {

    @Test
    void streamChatRegistersTaskAndBindsReturnedHandle() {
        ChatPipeLine chatPipeLine = mock(ChatPipeLine.class);
        MemoryService memoryService = mock(MemoryService.class);
        StreamTaskManager streamTaskManager = mock(StreamTaskManager.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        RagChatServiceImpl service = new RagChatServiceImpl(chatPipeLine, memoryService, streamTaskManager);

        when(chatPipeLine.execute(any(StreamChatContext.class))).thenReturn(handle);

        service.streamChat("什么是 RAG？", "conv-1", true, new SseEmitter(1000L));

        ArgumentCaptor<StreamChatContext> contextCaptor = ArgumentCaptor.forClass(StreamChatContext.class);
        verify(chatPipeLine).execute(contextCaptor.capture());
        StreamChatContext context = contextCaptor.getValue();
        assertThat(context.getQuestion()).isEqualTo("什么是 RAG？");
        assertThat(context.getConversationId()).isEqualTo("conv-1");
        assertThat(context.isDeepThinking()).isTrue();
        assertThat(context.getTaskId()).isNotBlank();
        assertThat(context.getCallback()).isNotNull();
        verify(streamTaskManager).register(any(), any(), any());
        verify(streamTaskManager).bindHandle(context.getTaskId(), handle);
    }

    @Test
    void stopTaskDelegatesToDistributedTaskManager() {
        ChatPipeLine chatPipeLine = mock(ChatPipeLine.class);
        MemoryService memoryService = mock(MemoryService.class);
        StreamTaskManager streamTaskManager = mock(StreamTaskManager.class);
        RagChatServiceImpl service = new RagChatServiceImpl(chatPipeLine, memoryService, streamTaskManager);

        service.stopTask("task-1");

        verify(streamTaskManager).cancel("task-1");
    }
}
