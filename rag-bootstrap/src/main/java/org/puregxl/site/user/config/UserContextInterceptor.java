package org.puregxl.site.user.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getDispatcherType() == DispatcherType.ASYNC || request.getDispatcherType() == DispatcherType.ERROR) {
            // SSE 超时或异常关闭后，容器可能用异步/错误分发重新进入 MVC；此时 Sa-Token 请求上下文可能已释放。
            return true;
        }
        // Sa-Token 会按配置从请求头、Cookie 等位置读取 token；未登录时抛出 NotLoginException，由全局异常处理返回统一结果。
        StpUtil.checkLogin();

        SaSession session = StpUtil.getSession();
        Object user = session.get("user");
        if (user instanceof UserInfoDTO userInfoDto) {
            UserContext.setUserContext(userInfoDto);
            return true;
        }
        String userId = getSessionString(session, "userId");
        if (userId != null) {
            UserContext.setUserContext(UserInfoDTO.builder()
                    .userId(userId)
                    .avatar(getSessionString(session, "avatar"))
                    .role(getSessionString(session, "role"))
                    .build());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserContext.removeUserContext();
    }

    private String getSessionString(SaSession session, String key) {
        Object value = session.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
