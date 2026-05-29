package org.puregxl.site.rag.core.retrieve.channel;

import lombok.*;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.enums.SearchChannelType;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchChannelResult {
    /**
     * 通道类型
     */
    private SearchChannelType searchChannelType;

    /**
     * 通道名称
     */
    private String channelName;

    /**
     * 检索到的结果
     */
    private List<RetrievedChunk> retrievedChunks;

    /**
     * 意图 ID -> 该意图命中的 Chunk 列表。
     * 定向检索通道会按命中的意图节点聚合结果，供上层统一拼装 KB 上下文。
     */
    private Map<String, List<RetrievedChunk>> intentChunks;
}
