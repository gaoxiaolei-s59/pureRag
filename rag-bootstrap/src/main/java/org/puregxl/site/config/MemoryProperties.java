package org.puregxl.site.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 记忆配置属性类
 * 用于配置 RAG 系统中的对话记忆管理相关参数
 * 包括历史轮数保留、缓存时间、摘要压缩等功能的配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.memory")
@Validated
public class MemoryProperties {

    /**
     * 保留原文的最近轮数（user+assistant 视为一轮）
     */
    @NotNull
    @Min(1)
    @Max(100)
    private Integer historyKeepTurns = 8;

    /**
     * 是否启用对话记忆压缩
     */
    private Boolean summaryEnabled = false;

    /** 首次生成摘要的轮数。未显式配置时，比原文窗口多一轮，兼容已有窗口配置。 */
    @Min(2)
    @Max(1000)
    private Integer summaryStartTurns;

    public int resolveSummaryStartTurns() {
        return summaryStartTurns == null ? historyKeepTurns + 1 : summaryStartTurns;
    }

    @AssertTrue(message = "开启会话摘要时，summary-start-turns 必须大于 history-keep-turns")
    public boolean isSummaryWindowValid() {
        return !Boolean.TRUE.equals(summaryEnabled) || historyKeepTurns == null
                || resolveSummaryStartTurns() > historyKeepTurns;
    }

    /**
     * 摘要最大字数
     */
    @NotNull
    @Min(200)
    @Max(1000)
    private Integer summaryMaxChars = 200;

    /** 模型标题及首问兜底标题的最大字符数。 */
    @Min(10)
    @Max(100)
    private int titleMaxLength = 30;
}
