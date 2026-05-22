package org.puregxl.site.user.dto.request;

import lombok.Data;

@Data
public class LoginRequest {
    /**
     * 用户名
     */
    private String userName;

    /**
     * 密码
     */
    private String password;
}
