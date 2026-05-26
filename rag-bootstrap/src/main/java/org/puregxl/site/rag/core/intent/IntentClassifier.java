package org.puregxl.site.rag.core.intent;

import java.util.List;

public interface IntentClassifier {

    /**
     * 查询全局意图图节点。
     * 实现类负责缓存命中、数据库回源以及树结构补齐，
     * 调用方只关心最终可直接使用的意图节点列表。
     */
    List<IntentNode> queryIntentNodes();
}
