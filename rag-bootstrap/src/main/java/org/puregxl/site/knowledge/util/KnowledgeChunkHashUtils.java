package org.puregxl.site.knowledge.util;

/**
 * Chunk 内容哈希工具。
 * <p>
 * contentHash 用于同一文档内的幂等和去重，统一使用 SHA-256，避免 Java hashCode 进程无关但碰撞概率较高的问题。
 */
public final class KnowledgeChunkHashUtils {

    private KnowledgeChunkHashUtils() {
    }

    public static String sha256(String content) {
        return KnowledgeContentHashUtils.sha256(content);
    }
}
