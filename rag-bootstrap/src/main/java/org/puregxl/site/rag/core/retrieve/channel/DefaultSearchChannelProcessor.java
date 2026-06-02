package org.puregxl.site.rag.core.retrieve.channel;

import lombok.RequiredArgsConstructor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认后处理器 => 实现简单的去重
 */
@Component
@RequiredArgsConstructor
public class DefaultSearchChannelProcessor implements SearchResultPostProcessor{
    @Override
    public String getName() {
        return "default-deduplicate";
    }

    @Override
    public int getOrder() {
        return 0;
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

        // 多通道结果已经按通道优先级合并，这里保留第一次出现的 Chunk，
        // 让意图定向检索命中的结果优先于全局兜底结果。
        Map<String, RetrievedChunk> deduplicatedChunks = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String deduplicateKey = resolveDeduplicateKey(chunk);
            deduplicatedChunks.putIfAbsent(deduplicateKey, chunk);
        }
        return List.copyOf(deduplicatedChunks.values());
    }

    private String resolveDeduplicateKey(RetrievedChunk chunk) {
        if (StrUtil.isNotBlank(chunk.getId())) {
            return "id:" + chunk.getId().trim();
        }
        if (StrUtil.isNotBlank(chunk.getText())) {
            return "text:" + chunk.getText().trim();
        }
        return "object:" + System.identityHashCode(chunk);
    }
}
