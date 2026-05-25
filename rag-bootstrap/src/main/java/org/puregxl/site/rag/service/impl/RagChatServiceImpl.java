package org.puregxl.site.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.web.SseEmitterSender;
import org.puregxl.site.infra.chat.StreamCallback;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.pipeline.ChatPipeLine;
import org.puregxl.site.rag.pipeline.StreamChatContext;
import org.puregxl.site.rag.service.MemoryService;
import org.puregxl.site.rag.service.RagChatService;
import org.puregxl.site.rag.service.handler.StreamTaskManager;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class RagChatServiceImpl implements RagChatService {

    private final ChatPipeLine chatPipeLine;
    private final MemoryService memoryService;
    private final StreamTaskManager streamTaskManager;

    /**
     * 发起一次 SSE 流式问答。
     * 主线程负责注册 taskId 与分布式取消上下文，真正的内容拼接和持久化在回调里完成。
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
                streamTaskManager.bindHandle(taskId, handle);
            }
        } catch (Exception ex) {
            if (streamTaskManager.markFailed(taskId)) {
                finished.set(true);
                sender.sendEvent("error", ex.getMessage());
                sender.fail(ex);
            }
            streamTaskManager.unregister(taskId);
        }
    }

    /**
     * 停止任务。
     * 分布式场景下不再直接操作本机内存句柄，而是通过任务管理器写共享取消标记并广播。
     */
    @Override
    public void stopTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        streamTaskManager.cancel(taskId);
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
        streamTaskManager.register(taskId, sender, () -> {
            finished.set(true);
            saveMemoryAfterComplete(conversationId, userId, userQuestion, answerBuilder, thinkingBuilder, startTimeMillis);
        });
        return new StreamCallback() {
            @Override
            public void onContent(String content) {
                if (streamTaskManager.markCancelledIfRequested(taskId)) {
                    finished.set(true);
                    return;
                }
                if (content != null) {
                    answerBuilder.append(content);
                }
                sender.sendEvent("message", content);
            }

            @Override
            public void onThinking(String content) {
                if (streamTaskManager.markCancelledIfRequested(taskId)) {
                    finished.set(true);
                    return;
                }
                if (content != null) {
                    thinkingBuilder.append(content);
                }
                sender.sendEvent("thinking", content);
            }

            @Override
            public void onComplete() {
                if (streamTaskManager.markCancelledIfRequested(taskId)) {
                    finished.set(true);
                    streamTaskManager.unregister(taskId);
                    return;
                }
                if (!streamTaskManager.markCompleted(taskId)) {
                    finished.set(true);
                    streamTaskManager.unregister(taskId);
                    return;
                }
                finished.set(true);
                saveMemoryAfterComplete(conversationId, userId, userQuestion, answerBuilder, thinkingBuilder, startTimeMillis);
                sender.sendEvent("done", taskId);
                sender.complete();
                streamTaskManager.unregister(taskId);
            }

            @Override
            public void onError(Throwable error) {
                if (streamTaskManager.markCancelledIfRequested(taskId)) {
                    finished.set(true);
                    streamTaskManager.unregister(taskId);
                    return;
                }
                if (!streamTaskManager.markFailed(taskId)) {
                    finished.set(true);
                    streamTaskManager.unregister(taskId);
                    return;
                }
                finished.set(true);
                sender.sendEvent("error", error == null ? "未知错误" : error.getMessage());
                sender.fail(error);
                streamTaskManager.unregister(taskId);
            }
        };
    }

    /**
     * 流式输出结束或取消后保存本轮对话。
     * 如果回答内容为空则不落记忆，避免写入空助手消息。
     */
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
