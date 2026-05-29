package org.puregxl.site.rag.core.retrieve.channel;

import org.puregxl.site.rag.core.retrieve.RetrievalContext;
import org.puregxl.site.rag.enums.SearchChannelType;
import org.puregxl.site.rag.pipeline.StreamChatContext;

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
    SearchChannelResult search(StreamChatContext context);


    /**
     * 是否启用该通道
     *
     * @param context 检索上下文
     * @return true 表示启用，false 表示跳过
     */
    boolean isEnabled(StreamChatContext context);



    /**
     * 通道名称
     * @return
     */
    SearchChannelType getType();


}
