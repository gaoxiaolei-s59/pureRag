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
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class RagChatServiceImpl implements RagChatService {

    private final ChatPipeLine chatPipeLine;
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
        SseEmitterSender sender = new SseEmitterSender(emitter);
        AtomicBoolean finished = new AtomicBoolean(false);
        sender.sendEvent("task", taskId);

        StreamChatContext context = StreamChatContext.builder()
                .question(userQuestion.trim())
                .conversationId(conversationId)
                .taskId(taskId)
                .deepThinking(Boolean.TRUE.equals(deepThinking))
                .userId(currentUserId())
                .callback(buildSseCallback(taskId, sender, finished))
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

    private StreamCallback buildSseCallback(String taskId, SseEmitterSender sender, AtomicBoolean finished) {
        return new StreamCallback() {
            @Override
            public void onContent(String content) {
                sender.sendEvent("message", content);
            }

            @Override
            public void onThinking(String content) {
                sender.sendEvent("thinking", content);
            }

            @Override
            public void onComplete() {
                finished.set(true);
                runningTasks.remove(taskId);
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

    private String currentUserId() {
        UserInfoDTO userContext = UserContext.getUserContext();
        return userContext == null ? null : userContext.getUserId();
    }
}
