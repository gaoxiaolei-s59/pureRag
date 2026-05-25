package org.puregxl.site.rag.core.rewrite;


import org.puregxl.site.infra.framework.convention.ChatMessage;

import java.util.List;

public interface QueryRewriteService {
    /**
     * 用户问题改写
     * @return
     */
    RewriteResult rewrite(String userQuestion, List<ChatMessage> history);
}
