package org.puregxl.site.rag.core.intent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.rag.dao.entity.IntentNodeDO;
import org.puregxl.site.rag.dao.mapper.IntentTreeMapper;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.enums.IntentLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认意图分类器。
 * 当前先承载“全局意图图装载”职责：优先命中 Redis 缓存，未命中时回源数据库，
 * 再把平铺节点补齐 children/fullPath/examples 等内存字段，方便后续继续扩展真正的意图分类逻辑。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultIntentClassifier implements IntentClassifier{

    private final IntentTreeCacheManager intentTreeCacheManager;
    private final IntentTreeMapper intentTreeMapper;

    /**
     * 查询全局意图图节点。
     * 这里返回的是平铺节点列表，但每个节点都带有 children/fullPath 等补齐后的树信息，
     * 因此既适合前端展示，也适合后续分类阶段直接复用。
     */
    @Override
    public List<IntentNode> queryIntentNodes() {
        List<IntentNode> cachedNodes = intentTreeCacheManager.getIntentCache();
        if (cachedNodes != null) {
            return cachedNodes;
        }

        LambdaQueryWrapper<IntentNodeDO> queryWrapper = Wrappers.lambdaQuery(IntentNodeDO.class)
                .eq(IntentNodeDO::getEnabled, 1)
                .orderByAsc(IntentNodeDO::getSortOrder, IntentNodeDO::getCreateTime, IntentNodeDO::getId);
        List<IntentNodeDO> nodeDOS = intentTreeMapper.selectList(queryWrapper);
        if (nodeDOS == null || nodeDOS.isEmpty()) {
            return List.of();
        }

        List<IntentNode> builtNodes = buildIntentNodes(nodeDOS);
        intentTreeCacheManager.setIntentCache(builtNodes);
        return builtNodes;
    }

    /**
     * 把数据库中的平铺记录转换成内存节点，并补齐树关系和全路径。
     * parent/child 关系仍使用 intentCode 作为业务主键，保证缓存和前端展示保持稳定。
     */
    private List<IntentNode> buildIntentNodes(List<IntentNodeDO> nodeDOS) {
        List<IntentNodeDO> sortedNodes = nodeDOS.stream()
                .sorted(Comparator
                        .comparing((IntentNodeDO node) -> node.getSortOrder() == null ? Integer.MAX_VALUE : node.getSortOrder())
                        .thenComparing(IntentNodeDO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(IntentNodeDO::getId, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<String, IntentNode> nodeById = new LinkedHashMap<>();
        for (IntentNodeDO nodeDO : sortedNodes) {
            IntentNode node = IntentNode.builder()
                    .id(nodeDO.getIntentCode())
                    .kbId(nodeDO.getKbId())
                    .name(nodeDO.getName())
                    .description(nodeDO.getDescription())
                    .level(IntentLevel.fromCode(nodeDO.getLevel()))
                    .parentId(nodeDO.getParentCode())
                    .examples(parseExamples(nodeDO.getExamples()))
                    .kind(IntentKind.fromCode(nodeDO.getKind()))
                    .collectionName(nodeDO.getCollectionName())
                    .mcpToolId(nodeDO.getMcpToolId())
                    .topK(nodeDO.getTopK())
                    .promptSnippet(nodeDO.getPromptSnippet())
                    .promptTemplate(nodeDO.getPromptTemplate())
                    .paramPromptTemplate(nodeDO.getParamPromptTemplate())
                    .build();
            nodeById.put(node.getId(), node);
        }

        for (IntentNode node : nodeById.values()) {
            if (StrUtil.isBlank(node.getParentId())) {
                node.setFullPath(StrUtil.blankToDefault(node.getName(), ""));
                continue;
            }
            IntentNode parent = nodeById.get(node.getParentId());
            if (parent == null) {
                node.setFullPath(StrUtil.blankToDefault(node.getName(), ""));
                continue;
            }
            parent.getChildren().add(node.getId());
            String parentPath = StrUtil.blankToDefault(parent.getFullPath(), parent.getName());
            node.setFullPath(StrUtil.isBlank(parentPath) ? StrUtil.blankToDefault(node.getName(), "") : parentPath + " > " + node.getName());
        }

        return new ArrayList<>(nodeById.values());
    }

    private List<String> parseExamples(String examplesJson) {
        if (StrUtil.isBlank(examplesJson)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(JSONUtil.parseArray(examplesJson), String.class);
        } catch (Exception ex) {
            log.warn("[意图图] examples 解析失败，按原始字符串兜底。examples={}", examplesJson, ex);
            return List.of(examplesJson);
        }
    }
}
