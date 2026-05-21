package org.puregxl.site.bootstrap.rag.pipeline;

import lombok.Builder;
import lombok.Data;
import org.puregxl.site.infra.chat.StreamCallback;

@Data
@Builder
public class StreamChatContext {
    private final String question;
    private final String conversationId;
    private final String taskId;
    private final boolean deepThinking;
    private final String userId;
    private final StreamCallback callback;
}
