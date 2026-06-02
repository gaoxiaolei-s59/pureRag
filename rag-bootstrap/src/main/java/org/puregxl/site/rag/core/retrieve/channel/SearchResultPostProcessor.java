package org.puregxl.site.rag.core.retrieve.channel;

import org.puregxl.site.infra.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * 搜索后处理器接口
 */
public interface SearchResultPostProcessor {
    /**
     * 后处理器名称
     * @return
     */
    String getName();

    /**
     * 获取优先级
     * @return
     */
    int getOrder();

    /**
     * 是否启用
     * @return
     */
    boolean isEnabled();

    /**
     * 执行后处理流水线。
     *
     * @param query 本次子问题原文，供 rerank 等语义处理器使用
     * @param chunks 上游通道或处理器输出的 Chunk 列表
     * @return 当前处理器输出的 Chunk 列表
     */
    List<RetrievedChunk> process(String query, List<RetrievedChunk> chunks);
}
