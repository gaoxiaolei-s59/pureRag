package org.puregxl.site.bootstrap.rag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.bootstrap.rag.config.RAGDefaultProperties;
import org.puregxl.site.bootstrap.rag.service.RagChatService;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Slf4j
@RequiredArgsConstructor
public class RagChatController {


    private final RagChatService ragChatService;

    private final RAGDefaultProperties ragDefaultProperties;

    /**
     * rag检索主入口
     * @return
     */
    @GetMapping(value = "/rag/v1/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam("userQuestion") String userQuestion,
                           @RequestParam(value = "conversationId", required = false) String conversationId,
                           @RequestParam(value = "deepThinking", required = false, defaultValue = "false") Boolean deepThinking) {
        SseEmitter emitter = new SseEmitter(ragDefaultProperties.getSseTimeoutMs());
        ragChatService.streamChat(userQuestion, conversationId, deepThinking, emitter);
        return emitter;
    }


    /**
     * 停止大模型调用
     * @param taskId
     * @return
     */
    @PostMapping(value = "/rag/v1/stop")
    public Result<Void> stop(@RequestParam("taskId") String taskId) {
        ragChatService.stopTask(taskId);
        return Results.success();
    }


}
