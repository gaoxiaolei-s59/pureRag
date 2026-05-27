package org.puregxl.site.rag.dto.resp;

import lombok.Data;

@Data
public class MemoryQueryResponse {
    /**
     * 职能user, assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;
}
