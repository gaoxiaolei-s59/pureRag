package org.puregxl.site.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.rag.pipeline.ChatPipeLine;
import org.puregxl.site.rag.pipeline.StreamChatContext;
import org.puregxl.site.rag.service.RagChatService;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.web.SseEmitterSender;
import org.puregxl.site.infra.chat.StreamCallback;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.service.MemoryService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class RagChatServiceImpl implements RagChatService {

    private final ChatPipeLine chatPipeLine;
    private final MemoryService memoryService;
    private final Map<String, StreamCancellationHandle> runningTasks = new ConcurrentHashMap<>();

    /**
     * 发起一次 SSE 流式问答
     * @param userQuestion
     * @param conversationId
     * @param deepThinking
     * @param emitter
     */
    @Override
    public void streamChat(String userQuestion, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        if (StrUtil.isBlank(userQuestion)) {
            throw new ClientException("用户问题不能为空");
        }

        String taskId = IdUtil.fastSimpleUUID();
        String currentUserId = currentUserId();
        SseEmitterSender sender = new SseEmitterSender(emitter);
        AtomicBoolean finished = new AtomicBoolean(false);
        sender.sendEvent("task", taskId);

        StreamChatContext context = StreamChatContext.builder()
                .question(userQuestion.trim())
                .conversationId(conversationId)
                .taskId(taskId)
                .deepThinking(Boolean.TRUE.equals(deepThinking))
                .userId(currentUserId)
                .callback(buildSseCallback(taskId, conversationId, currentUserId, userQuestion.trim(), sender, finished))
                .build();

        try {
            StreamCancellationHandle handle = chatPipeLine.execute(context);
            if (!finished.get() && handle != null) {
                runningTasks.put(taskId, handle);
            }
        } catch (Exception ex) {
            runningTasks.remove(taskId);
            sender.sendEvent("error", ex.getMessage());
            sender.fail(ex);
        }

    }

    /**
     * 停止任务
     * @param taskId
     */
    @Override
    public void stopTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        StreamCancellationHandle handle = runningTasks.remove(taskId);
        if (handle != null) {
            handle.cancel();
        }
    }

    /**
     * 构建 SSE 流式回调。
     * <p>
     * 这里同时承担“完整回答拼接”的职责：onContent/onThinking 只向前端推送增量，
     * onComplete 时再把本轮用户问题和助手完整回复写入对话记忆表。
     */
    private StreamCallback buildSseCallback(String taskId,
                                            String conversationId,
                                            String userId,
                                            String userQuestion,
                                            SseEmitterSender sender,
                                            AtomicBoolean finished) {
        StringBuilder answerBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        long startTimeMillis = System.currentTimeMillis();
        return new StreamCallback() {
            @Override
            public void onContent(String content) {
                if (content != null) {
                    answerBuilder.append(content);
                }
                sender.sendEvent("message", content);
            }

            @Override
            public void onThinking(String content) {
                if (content != null) {
                    thinkingBuilder.append(content);
                }
                sender.sendEvent("thinking", content);
            }

            @Override
            public void onComplete() {
                finished.set(true);
                runningTasks.remove(taskId);
                saveMemoryAfterComplete(conversationId, userId, userQuestion, answerBuilder, thinkingBuilder, startTimeMillis);
                sender.sendEvent("done", taskId);
                sender.complete();
            }

            @Override
            public void onError(Throwable error) {
                finished.set(true);
                runningTasks.remove(taskId);
                sender.sendEvent("error", error == null ? "未知错误" : error.getMessage());
                sender.fail(error);
            }
        };
    }

    private void saveMemoryAfterComplete(String conversationId,
                                         String userId,
                                         String userQuestion,
                                         StringBuilder answerBuilder,
                                         StringBuilder thinkingBuilder,
                                         long startTimeMillis) {
        String answer = answerBuilder.toString();
        if (StrUtil.isBlank(answer)) {
            return;
        }
        String thinkingContent = thinkingBuilder.isEmpty() ? null : thinkingBuilder.toString();
        Integer thinkingDuration = thinkingBuilder.isEmpty()
                ? null
                : Math.toIntExact(Math.max(1L, (System.currentTimeMillis() - startTimeMillis) / 1000L));
        memoryService.saveConversationTurn(
                userId,
                conversationId,
                ChatMessage.user(userQuestion),
                ChatMessage.assistant(answer, thinkingContent, thinkingDuration));
    }

    private String currentUserId() {
        UserInfoDTO userContext = UserContext.getUserContext();
        return userContext == null ? null : userContext.getUserId();
    }
}
