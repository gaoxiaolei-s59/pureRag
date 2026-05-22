package org.puregxl.site.user.dto.response;

import lombok.Data;

@Data
public class LoginResponse {
    /**
     * 返回token
     */
    private String token;

    /**
     * 用户职位
     */
    private String role;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 用户id
     */
    private String userId;
}
