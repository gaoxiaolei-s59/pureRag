package org.puregxl.site.user.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@TableName("t_user")
public class UserDO {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String username;

    private String password;

    /**
     * 用户头像 URL
     */
    private String avatar;

    /**
     * 角色：admin / user
     */
    private String role;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
