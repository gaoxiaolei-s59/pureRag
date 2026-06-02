package org.puregxl.site.rag.core.retrieve.channel;

import lombok.RequiredArgsConstructor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.infra.rerank.RerankService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 重排序后处理器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RerankSearchChannelProcess implements SearchResultPostProcessor{

    private final RerankService rerankService;

    @Override
    public String getName() {
        return "rerank";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<RetrievedChunk> process(String query, List<RetrievedChunk> chunks, int topK) {
        if (CollUtil.isEmpty(chunks)) {
            return List.of();
        }
        int safeTopK = resolveTopK(chunks, topK);
        if (StrUtil.isBlank(query)) {
            return chunks.stream().limit(safeTopK).toList();
        }

        try {
            List<RetrievedChunk> rerankedChunks = rerankService.rerank(query.trim(), chunks, safeTopK);
            if (CollUtil.isEmpty(rerankedChunks)) {
                return chunks.stream().limit(safeTopK).toList();
            }
            return rerankedChunks;
        } catch (Exception e) {
            // Rerank 属于质量增强能力，失败时不能中断主检索链路，直接退回去重后的召回顺序。
            log.error("检索结果 rerank 失败，降级使用召回顺序，query：{}", query, e);
            return chunks.stream().limit(safeTopK).toList();
        }
    }

    private int resolveTopK(List<RetrievedChunk> chunks, int topK) {
        if (topK <= 0) {
            return chunks.size();
        }
        return Math.min(topK, chunks.size());
    }
}
