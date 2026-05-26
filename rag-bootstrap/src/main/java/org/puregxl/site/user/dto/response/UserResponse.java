package org.puregxl.site.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    /**
     * 用户名
     */
    private String userName;

    /**
     * 头像地址
     */
    private String avatar;
}
