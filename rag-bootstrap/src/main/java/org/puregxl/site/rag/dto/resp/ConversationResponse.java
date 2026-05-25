package org.puregxl.site.rag.dto.resp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.List;

@Data
public class ConversationResponse {
    /**
     * 会话ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话描述/备注
     */
    private String description;


    /**
     * 是否开启深度思考：0-关闭，1-开启
     */
    private Integer deepThinking;

    /**
     * 是否置顶：0-否，1-是
     */
    private Integer pinned;
}
