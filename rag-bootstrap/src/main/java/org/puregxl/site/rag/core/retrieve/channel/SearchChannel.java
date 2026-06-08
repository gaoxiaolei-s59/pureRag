package org.puregxl.site.rag.core.retrieve.channel;

import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.enums.SearchChannelType;

/**
 * KB检索通道接口
 */
public interface SearchChannel {
    /**
     * 获取名字
     * @return
     */
    String getName();

    /**
     * 优先级
     * @return
     */
    Integer priority();

    /**
     * 执行检索
     * @return
     */
    SearchChannelResult search(SubQuestionIntent subIntent, int defaultTopK);


    /**
     * 是否启用该通道
     *
     * @param subIntent 子问题上下文
     * @return true 表示启用，false 表示跳过
     */
    boolean isEnabled(SubQuestionIntent subIntent);



    /**
     * 通道名称
     * @return
     */
    SearchChannelType getType();

}
