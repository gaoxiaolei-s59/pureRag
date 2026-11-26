package org.puregxl.site.rag.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.puregxl.site.rag.dao.entity.ConversationDO;

public interface ConversationMapper extends BaseMapper<ConversationDO> {

    /** 原子取得会话行锁并更新最近活动时间；元信息尚未保存时，使用首问兜底。 */
    @Insert("""
            INSERT INTO t_conversation (id, user_id, title, description, pinned, deep_thinking,
                                        del_flag, create_time, update_time)
            VALUES (#{conversationId}, #{userId}, #{title}, #{description}, 0, 0, 0,
                    CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE update_time =
                IF(del_flag = 0 AND user_id = #{userId}, CURRENT_TIMESTAMP(3), update_time)
            """)
    void touchForTurn(@Param("conversationId") String conversationId, @Param("userId") String userId,
                      @Param("title") String title, @Param("description") String description);

    /** 在当前事务中锁定归属当前用户且有效的会话。所有写入者先锁会话，再写消息或摘要。 */
    default void lockOwnedConversation(String conversationId, String userId) {
        ConversationDO row = selectOne(Wrappers.lambdaQuery(ConversationDO.class)
                .select(ConversationDO::getId).eq(ConversationDO::getId, conversationId)
                .eq(ConversationDO::getUserId, userId).last("FOR UPDATE"));
        if (row == null) {
            throw new IllegalStateException("会话已删除或不属于当前用户");
        }
    }
}
