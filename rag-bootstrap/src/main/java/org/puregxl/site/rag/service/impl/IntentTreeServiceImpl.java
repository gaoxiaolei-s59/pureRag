package org.puregxl.site.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.IntentTreeCacheManager;
import org.puregxl.site.rag.dao.entity.IntentNodeDO;
import org.puregxl.site.rag.dao.mapper.IntentTreeMapper;
import org.puregxl.site.rag.dto.resp.IntentNodeResponse;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.enums.IntentLevel;
import org.puregxl.site.rag.service.IntentTreeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntentTreeServiceImpl implements IntentTreeService {

    private final IntentTreeCacheManager intentTreeCacheManager;
    private final IntentTreeMapper intentTreeMapper;

    /**
     * 查询全局意图图。
     * 主流程是：优先从 Redis 读取全局缓存；缓存未命中时回源数据库，
     * 把平铺节点补齐 children/fullPath/examples 等内存字段后回写缓存，
     * 最终再按稳定顺序返回给前端。
     */
    @Override
    public List<IntentNodeResponse> queryIntentNode() {
        List<IntentNode> cachedNodes = intentTreeCacheManager.getIntentCache();
        if (cachedNodes != null) {
            return toResponses(cachedNodes);
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
        return toResponses(builtNodes);
    }

    /**
     * 把数据库中的平铺记录转换成内存节点，并补齐树关系和全路径。
     * 这里缓存的仍然是平铺节点列表，但每个节点的 children/fullPath 都会预先算好，
     * 方便后续命中缓存时直接用于展示或意图识别。
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
            return List.of(examplesJson);
        }
    }

    private List<IntentNodeResponse> toResponses(List<IntentNode> nodes) {
        return nodes.stream()
                .map(node -> BeanUtil.copyProperties(node, IntentNodeResponse.class))
                .toList();
    }
}
