package org.puregxl.site.rag.pipeline;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import org.puregxl.site.infra.chat.StreamCallback;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.rewrite.MutiQueryRewriteService;
import org.puregxl.site.rag.core.rewrite.RewriteResult;

import java.util.List;

@Data
@Builder
public class StreamChatContext {
    private final String question;
    private final String conversationId;
    private final String taskId;
    private final boolean deepThinking;
    private final String userId;
    private final StreamCallback callback;


    @Setter
    private List<ChatMessage> history;

    @Setter
    private RewriteResult rewriteResult;
}
