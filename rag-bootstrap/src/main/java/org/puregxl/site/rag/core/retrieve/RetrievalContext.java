package org.puregxl.site.rag.core.retrieve;

import cn.hutool.core.util.StrUtil;
import lombok.Builder;
import lombok.Data;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;

import java.util.List;
import java.util.Map;

/**
 * 检索上下文（MCP + KB 结果的统一承载）
 */
@Data
@Builder
public class RetrievalContext {
    /**
     * MCP 召回的上下文
     */
    private String mcpContext;

    /**
     * KB 召回的上下文
     */
    private String kbContext;

    /**
     * 意图 ID -> 分片列表
     */
    private Map<String, List<RetrievedChunk>> intentChunks;

    /**
     * 是否存在 MCP 上下文
     */
    public boolean hasMcp() {
        return StrUtil.isNotBlank(mcpContext);
    }

    /**
     * 是否存在 KB 上下文
     */
    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext);
    }

    /**
     * 是否无任何上下文
     */
    public boolean isEmpty() {
        return !hasMcp() && !hasKb();
    }
}
