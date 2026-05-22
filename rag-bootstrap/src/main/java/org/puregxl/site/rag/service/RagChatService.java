package org.puregxl.site.rag.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagChatService {
    void streamChat(String userQuestion, String conversationId, Boolean deepThinking, SseEmitter emitter);

    void stopTask(String taskId);
}
