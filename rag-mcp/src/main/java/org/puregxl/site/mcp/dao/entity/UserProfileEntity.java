package org.puregxl.site.mcp.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表实体。
 */
@Data
@TableName("user_profile")
public class UserProfileEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
