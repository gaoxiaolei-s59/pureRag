package org.puregxl.site.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.user.dto.request.LoginRequest;
import org.puregxl.site.user.dto.response.LoginResponse;
import org.puregxl.site.user.service.AuthService;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录接口
     * @param request
     * @return 返回token
     */
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Results.success(authService.login(request));
    }


    /**
     * 用户登录接口
     * @return 返回token
     */
    @PostMapping("/auth/test")
    public Result<String> testLogin() {
        return Results.success("ok");
    }


}
