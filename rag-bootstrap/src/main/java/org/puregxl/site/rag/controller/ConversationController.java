package org.puregxl.site.rag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.puregxl.site.rag.dto.resp.ConversationResponse;
import org.puregxl.site.rag.service.ConversationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/conversation")
@Slf4j
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 查询用户会话列表
     * @param userId
     * @return
     */
    @GetMapping
    public Result<List<ConversationResponse>> queryConversation(@RequestParam("userId") String userId) {
        return Results.success(conversationService.queryConversation(userId));
    }

}
