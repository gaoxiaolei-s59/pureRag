package org.puregxl.site.bootstrap.user.context;

public class UserContext {
    private static final ThreadLocal<UserInfoDTO> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 移除用户上下文
     */
    public static void removeUserContext() {
        THREAD_LOCAL.remove();
    }


    /**
     * 设置用户上下文
     */
    public static void setUserContext(UserInfoDTO userContext) {
        THREAD_LOCAL.set(userContext);
    }

    /**
     * 获取用户上下文
     */
    public static UserInfoDTO getUserContext() {
        return THREAD_LOCAL.get();
    }
}
