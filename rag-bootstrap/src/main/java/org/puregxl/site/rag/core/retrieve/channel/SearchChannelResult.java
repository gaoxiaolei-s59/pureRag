package org.puregxl.site.rag.core.retrieve.channel;

import lombok.*;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.enums.SearchChannelType;

import java.util.List;

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


}
