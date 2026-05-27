package org.puregxl.site.rag.core.intent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.rag.dao.entity.IntentNodeDO;
import org.puregxl.site.rag.dao.mapper.IntentTreeMapper;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.enums.IntentLevel;
import org.puregxl.site.rag.support.PromptTemplateLoader;
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
    private static final String INTENT_CLASSIFY_PROMPT_RESOURCE_PATH = "prompt/intent-classify-prompt.txt";

    private final IntentTreeCacheManager intentTreeCacheManager;
    private final IntentTreeMapper intentTreeMapper;
    private final LLMService llmService;
    private final PromptTemplateLoader promptTemplateLoader;

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
     * 调用大模型完成意图图匹配。
     * 这里只把叶子节点作为候选，避免父节点/中间节点和真正可命中的末级意图混在一起影响判断。
     */
    @Override
    public List<NodeScore> classifiy(String question) {
        if (StrUtil.isBlank(question)) {
            return List.of();
        }

        List<IntentNode> candidates = queryIntentNodes().stream()
                .filter(IntentNode::isLeaf)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        try {
            String response = llmService.chat(buildIntentClassifyPrompt(question.trim(), candidates));
            return parseNodeScores(response, candidates);
        } catch (Exception ex) {
            log.warn("[意图识别] 模型匹配失败，降级为空结果。question={}", question, ex);
            return List.of();
        }
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
                    .recordId(nodeDO.getId())
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

    private String buildIntentClassifyPrompt(String question, List<IntentNode> candidates) {
        return promptTemplateLoader.load(INTENT_CLASSIFY_PROMPT_RESOURCE_PATH)
                .replace("{{question}}", question)
                .replace("{{intentCandidates}}", buildCandidateText(candidates));
    }

    private String buildCandidateText(List<IntentNode> candidates) {
        StringBuilder builder = new StringBuilder();
        for (IntentNode candidate : candidates) {
            builder.append("- id: ").append(candidate.getId()).append("\n")
                    .append("  name: ").append(StrUtil.blankToDefault(candidate.getName(), "")).append("\n")
                    .append("  fullPath: ").append(StrUtil.blankToDefault(candidate.getFullPath(), "")).append("\n")
                    .append("  description: ").append(StrUtil.blankToDefault(candidate.getDescription(), "")).append("\n")
                    .append("  examples: ")
                    .append(candidate.getExamples() == null || candidate.getExamples().isEmpty()
                            ? "[]"
                            : String.join(" | ", candidate.getExamples()))
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private List<NodeScore> parseNodeScores(String response, List<IntentNode> candidates) {
        if (StrUtil.isBlank(response)) {
            return List.of();
        }
        JsonObject root = JsonParser.parseString(extractJson(response)).getAsJsonObject();
        if (!root.has("matches") || !root.get("matches").isJsonArray()) {
            return List.of();
        }

        Map<String, IntentNode> candidateMap = candidates.stream()
                .collect(LinkedHashMap::new, (map, node) -> map.put(node.getId(), node), LinkedHashMap::putAll);
        JsonArray matches = root.getAsJsonArray("matches");
        List<NodeScore> scores = new ArrayList<>();
        matches.forEach(item -> {
            if (item == null || !item.isJsonObject()) {
                return;
            }
            JsonObject match = item.getAsJsonObject();
            String id = match.has("id") && !match.get("id").isJsonNull() ? match.get("id").getAsString() : null;
            if (StrUtil.isBlank(id) || !candidateMap.containsKey(id)) {
                return;
            }
            double score = match.has("score") && !match.get("score").isJsonNull() ? match.get("score").getAsDouble() : 0D;
            scores.add(NodeScore.builder()
                    .score(score)
                    .intentNode(candidateMap.get(id))
                    .build());
        });
        scores.sort(Comparator.comparing(NodeScore::getScore).reversed());
        return scores;
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return trimmed;
        }
        return trimmed.substring(start, end + 1);
    }
}
