package org.puregxl.site.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.IntentClassifier;
import org.puregxl.site.rag.dto.resp.IntentNodeResponse;
import org.puregxl.site.rag.service.IntentTreeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IntentTreeServiceImpl implements IntentTreeService {

    private final IntentClassifier intentClassifier;

    /**
     * 查询全局意图图响应。
     * 这里不再承担缓存、查库和树构建职责，只负责把分类器返回的领域节点转换成前端响应对象，
     * 保持 Service 层编排职责单一，方便后续替换不同的分类器实现。
     */
    @Override
    public List<IntentNodeResponse> queryIntentNode() {
        return toResponses(intentClassifier.queryIntentNodes());
    }

    private List<IntentNodeResponse> toResponses(List<IntentNode> nodes) {
        return nodes.stream()
                .map(node -> BeanUtil.copyProperties(node, IntentNodeResponse.class))
                .toList();
    }
}
