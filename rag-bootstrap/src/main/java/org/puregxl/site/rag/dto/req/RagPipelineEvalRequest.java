package org.puregxl.site.rag.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 评测旁路请求。
 * 与正式 `/rag/v1/chat` 保持同一组核心入参，但只跑生成前链路。
 */
@Data
public class RagPipelineEvalRequest {

    /**
     * 用户问题。
     */
    @NotBlank(message = "用户问题不能为空")
    private String userQuestion;

    /**
     * 会话 ID，可为空；传入后会复用记忆加载逻辑。
     */
    private String conversationId;

    /**
     * 是否开启深度思考。
     */
    private Boolean deepThinking = Boolean.FALSE;
}
