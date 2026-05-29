package org.puregxl.site.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.IntentClassifier;
import org.puregxl.site.rag.core.intent.IntentTreeCacheManager;
import org.puregxl.site.rag.dao.entity.IntentNodeDO;
import org.puregxl.site.rag.dao.mapper.IntentTreeMapper;
import org.puregxl.site.rag.dto.req.IntentNodeCreateRequest;
import org.puregxl.site.rag.dto.req.IntentNodeUpdateRequest;
import org.puregxl.site.rag.dto.resp.IntentNodeResponse;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.rag.service.IntentTreeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IntentTreeServiceImpl implements IntentTreeService {
    private final IntentClassifier intentClassifier;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IntentTreeMapper intentTreeMapper;
    private final IntentTreeCacheManager intentTreeCacheManager;

    /**
     * 查询全局意图图响应。 * 这里不再承担缓存、查库和树构建职责，只负责把分类器返回的领域节点转换成前端响应对象， * 保持 Service 层编排职责单一，方便后续替换不同的分类器实现。
     */
    @Override
    public List<IntentNodeResponse> queryIntentNode() {
        return toResponses(intentClassifier.queryIntentNodes());
    }

    /**
     * 按数据库主键查询意图节点详情。 * 编辑页面需要拿到完整配置字段，因此这里直接基于落库对象组装响应。
     */
    @Override
    public IntentNodeResponse getIntentNodeById(String id) {
        if (StrUtil.isBlank(id)) {
            throw new ClientException("意图节点 ID 不能为空");
        }
        IntentNodeDO existing = getRequiredNode(id);
        return toResponse(existing);
    }

    /**
     * 创建意图节点。 * 这里直接落库扁平节点数据，并在成功后清理意图树缓存， * 保证下一次查询会基于最新数据重新构建树结构。
     */
    @Override
    public void createIntentNode(IntentNodeCreateRequest request) {
        if (request == null) {
            throw new ClientException("意图节点创建参数不能为空");
        }
        intentTreeMapper.insert(IntentNodeDO.builder().kbId(request.getKbId()).intentCode(request.getIntentCode()).name(request.getName()).level(request.getLevel()).parentCode(request.getParentCode()).description(request.getDescription()).examples(toExamplesJson(request.getExamples())).collectionName(resolveCollectionName(request.getKbId(), request.getCollectionName())).mcpToolId(request.getMcpToolId()).topK(request.getTopK()).kind(request.getKind()).sortOrder(request.getSortOrder()).enabled(request.getEnabled()).promptSnippet(request.getPromptSnippet()).promptTemplate(request.getPromptTemplate()).paramPromptTemplate(request.getParamPromptTemplate()).build());
        intentTreeCacheManager.clear();
    }

    /**
     * 按数据库主键更新意图节点。 * 先查出现有节点，再在原对象上覆盖可变字段，避免把未传字段误更新为空。
     */
    @Override
    public void updateIntentNode(String id, IntentNodeUpdateRequest request) {
        if (StrUtil.isBlank(id)) {
            throw new ClientException("意图节点 ID 不能为空");
        }
        if (request == null) {
            throw new ClientException("意图节点更新参数不能为空");
        }
        IntentNodeDO existing = getRequiredNode(id);
        existing.setKbId(request.getKbId());
        existing.setIntentCode(request.getIntentCode());
        existing.setName(request.getName());
        existing.setLevel(request.getLevel());
        existing.setParentCode(request.getParentCode());
        existing.setDescription(request.getDescription());
        existing.setExamples(toExamplesJson(request.getExamples()));
        existing.setCollectionName(resolveCollectionName(request.getKbId(), request.getCollectionName()));
        existing.setMcpToolId(request.getMcpToolId());
        existing.setTopK(request.getTopK());
        existing.setKind(request.getKind());
        existing.setSortOrder(request.getSortOrder());
        existing.setEnabled(request.getEnabled());
        existing.setPromptSnippet(request.getPromptSnippet());
        existing.setPromptTemplate(request.getPromptTemplate());
        existing.setParamPromptTemplate(request.getParamPromptTemplate());
        intentTreeMapper.updateById(existing);
        intentTreeCacheManager.clear();
    }

    /**
     * 按数据库主键删除意图节点。 * 删除前先校验节点存在，避免前端误删时静默成功。
     */
    @Override
    public void deleteIntentNode(String id) {
        if (StrUtil.isBlank(id)) {
            throw new ClientException("意图节点 ID 不能为空");
        }
        getRequiredNode(id);
        intentTreeMapper.deleteById(id);
        intentTreeCacheManager.clear();
    }

    private List<IntentNodeResponse> toResponses(List<IntentNode> nodes) {
        return nodes.stream().map(node -> BeanUtil.copyProperties(node, IntentNodeResponse.class)).toList();
    }

    private IntentNodeResponse toResponse(IntentNodeDO nodeDO) {
        IntentNodeResponse response = new IntentNodeResponse();
        response.setRecordId(nodeDO.getId());
        response.setId(nodeDO.getIntentCode());
        response.setKbId(nodeDO.getKbId());
        response.setName(nodeDO.getName());
        response.setDescription(nodeDO.getDescription());
        response.setExamples(parseExamples(nodeDO.getExamples()));
        response.setLevel(org.puregxl.site.rag.enums.IntentLevel.fromCode(nodeDO.getLevel()));
        response.setParentId(nodeDO.getParentCode());
        response.setCollectionName(nodeDO.getCollectionName());
        response.setMcpToolId(nodeDO.getMcpToolId());
        response.setKind(org.puregxl.site.rag.enums.IntentKind.fromCode(nodeDO.getKind()));
        response.setTopK(nodeDO.getTopK());
        response.setSortOrder(nodeDO.getSortOrder());
        response.setEnabled(nodeDO.getEnabled());
        response.setPromptSnippet(nodeDO.getPromptSnippet());
        response.setPromptTemplate(nodeDO.getPromptTemplate());
        response.setParamPromptTemplate(nodeDO.getParamPromptTemplate());
        return response;
    }

    private IntentNodeDO getRequiredNode(String id) {
        IntentNodeDO existing = intentTreeMapper.selectById(id);
        if (existing == null) {
            throw new ClientException("意图节点不存在");
        }
        return existing;
    }

    private String toExamplesJson(List<String> examples) {
        if (examples == null) {
            return null;
        }
        return JSONUtil.toJsonStr(examples);
    }

    private List<String> parseExamples(String examplesJson) {
        if (StrUtil.isBlank(examplesJson)) {
            return List.of();
        }
        return JSONUtil.toList(JSONUtil.parseArray(examplesJson), String.class);
    }

    /**
     * collectionName 优先根据 kbId 反查知识库配置。 * 这样前端只传 kbId 就能创建/更新 KB 类型意图；查不到知识库时再回退显式入参，兼容老调用方。
     */
    private String resolveCollectionName(String kbId, String explicitCollectionName) {
        if (StrUtil.isNotBlank(kbId)) {
            KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectById(kbId);
            if (knowledgeBase != null && StrUtil.isNotBlank(knowledgeBase.getCollectionName())) {
                return knowledgeBase.getCollectionName();
            }
        }
        return explicitCollectionName;
    }
}