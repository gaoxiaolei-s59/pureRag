package org.puregxl.site.bootstrap.knowledge.util;

import org.puregxl.site.framework.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 知识库内容哈希工具。
 * <p>
 * 文档级哈希基于文件二进制内容生成，用于定时拉取时判断源文件是否变化；Chunk 级哈希基于文本内容生成，
 * 用于同一文档内分块去重。两者统一使用 SHA-256，保证不同流程里的哈希语义稳定。
 */
public final class KnowledgeContentHashUtils {

    private KnowledgeContentHashUtils() {
    }

    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception ex) {
            throw new ServiceException("生成内容哈希失败：" + ex.getMessage());
        }
    }

    public static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }
}
