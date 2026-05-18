package org.puregxl.site.bootstrap.user.context;

import lombok.Data;

@Data
public class UserInfoDTO {
    /**
     * 用户id
     */
    private String userId;

    /**
     * 用户头像地址
     */
    private String avatar;

    /**
     * 角色：admin / user
     */
    private String role;



}
