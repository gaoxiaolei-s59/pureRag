package org.puregxl.site.rag.service;

import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.rag.dto.resp.ConversationResponse;

import java.util.List;

public interface ConversationService {
    /**
     * 查询用户对应的会话
     * @param userId
     * @return
     */
    List<ConversationResponse> queryConversation(String userId);
}
