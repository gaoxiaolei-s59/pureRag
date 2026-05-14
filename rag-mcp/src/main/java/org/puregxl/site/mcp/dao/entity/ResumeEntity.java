package org.puregxl.site.mcp.dao.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 简历表实体。
 */
@Data
@TableName("resume")
public class ResumeEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 简历ID。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 简历名称。
     */
    private String resumeName;

    /**
     * 状态：ACTIVE启用，INACTIVE停用。
     */
    private String status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
