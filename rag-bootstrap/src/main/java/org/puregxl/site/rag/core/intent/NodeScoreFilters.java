package org.puregxl.site.rag.core.intent;

import cn.hutool.core.util.StrUtil;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class NodeScoreFilters {
    /**
     * 过滤mcp节点
     *
     * @param nodeScores
     * @return
     */
    public List<NodeScore> mcp(List<NodeScore> nodeScores) {
        return nodeScores.stream()
                .filter(nodeScore -> nodeScore.getIntentNode() != null && nodeScore.getIntentNode().isMCP())
                .filter(nodeScore -> StrUtil.isNotBlank(nodeScore.getIntentNode().getMcpToolId()))
                .toList();
    }


    /**
     * 过滤KB节点
     *
     * @param nodeScores
     * @return
     */
    public List<NodeScore> kb(List<NodeScore> nodeScores) {
        return nodeScores.stream()
                .filter(nodeScore -> nodeScore.getIntentNode() != null && nodeScore.getIntentNode().isKB())
                .filter(nodeScore -> StrUtil.isNotBlank(nodeScore.getIntentNode().getKbId()))
                .toList();
    }

}
