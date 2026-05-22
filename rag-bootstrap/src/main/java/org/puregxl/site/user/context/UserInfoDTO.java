package org.puregxl.site.user.context;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
