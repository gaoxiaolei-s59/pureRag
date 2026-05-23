package org.puregxl.site.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 会话摘要
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@TableName("t_conversation_summary")
public class ConversationSummerDO {
    /**
     * 主键 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 用户 ID
     */
    private String UserId;

    /**
     * 摘要内容
     */
    private String SummaryContent;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 删除标志(0-未删除,1-已删除)
     */
    @TableLogic
    private Integer delFlag;
}
