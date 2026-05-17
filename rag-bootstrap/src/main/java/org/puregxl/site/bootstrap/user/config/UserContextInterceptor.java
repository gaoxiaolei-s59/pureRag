package org.puregxl.site.bootstrap.user.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.puregxl.site.bootstrap.user.context.UserContext;
import org.puregxl.site.bootstrap.user.context.UserInfoDto;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Sa-Token 会按配置从请求头、Cookie 等位置读取 token；未登录时抛出 NotLoginException，由全局异常处理返回统一结果。
        StpUtil.checkLogin();

        Object user = StpUtil.getSession().get("user");
        if (user instanceof UserInfoDto userInfoDto) {
            UserContext.setUserContext(userInfoDto);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserContext.removeUserContext();
    }
}
