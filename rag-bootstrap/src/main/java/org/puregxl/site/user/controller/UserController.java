package org.puregxl.site.user.controller;


import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.dto.response.UserResponse;
import org.puregxl.site.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping("/api/user")
    public Result<UserResponse> queryUserBaseInformation(){
        UserResponse response = BeanUtil.copyProperties(
                UserContext.getUserContext(),
                UserResponse.class
        );
        return Results.success(response);
    }
}
