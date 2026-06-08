package org.puregxl.site.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "rag.search")
@Data
public class SearchChannelProperties {
    /**
     * 默认返回的 Top
     */
    private int defaultTopK = 20;


    private Channels channels = new Channels();

    @Data
    public static class Channels{

        private VectorChannel vectorChannel;

        private IntentChannel intentChannel;
    }


    @Data
    public static class VectorChannel{
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 意图置信度阈值
         * 当意图识别的最高分数低于此阈值时，启用全局检索
         */
        private double confidenceThreshold = 0.6;


        /**
         * TopK 倍数
         * 全局检索时召回更多候选，后续通过 Rerank 筛选
         */
        private int topKMultiplier = 3;
    }

    @Data
    public static class IntentChannel{
        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 最低意图分数
         * 低于此分数的意图节点会被过滤
         */
        private double minIntentScore = 0.4;

        /**
         * TopK 倍数
         */
        private int topKMultiplier = 2;
    }


}
