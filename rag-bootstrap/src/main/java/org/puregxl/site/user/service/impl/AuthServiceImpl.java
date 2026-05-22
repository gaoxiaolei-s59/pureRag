package org.puregxl.site.user.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.SaJsonConvertException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.user.dao.entity.UserDO;
import org.puregxl.site.user.dao.mapper.UserMapper;
import org.puregxl.site.user.dto.request.LoginRequest;
import org.puregxl.site.user.dto.response.LoginResponse;
import org.puregxl.site.user.service.AuthService;
import org.puregxl.site.framework.exception.ClientException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 用户权证接口
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    /**
     * 用户登录
     * @param request
     * @return
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        String userName = request.getUserName();
        String password = request.getPassword();

        if (StrUtil.isBlank(userName) || StrUtil.isBlank(password)) {
            throw new ClientException("用户名或者密码不能为空");
        }
        UserDO user = findByUsername(userName);
        if (user == null || !Objects.equals(password, user.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }

        if (user.getId() == null) {
            throw new ClientException("用户信息异常");
        }

        doLogin(user.getId());
        saveUserSession(user);

        LoginResponse response = new LoginResponse();
        response.setToken(StpUtil.getTokenValue());
        response.setUserId(user.getId());
        response.setAvatar(user.getAvatar());
        response.setRole(user.getRole());
        return response;
    }


    /**
     * 根据用户名查询用户实体
     * @param username
     * @return
     */
    private UserDO findByUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return null;
        }
        return userMapper.selectOne(
                Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username)
        );
    }

    /**
     * 执行 Sa-Token 登录。
     * Redis 持久化打开后，Sa-Token 会在登录时读取同一 loginId 的历史 Session；如果项目包名调整过，
     * 历史 Session 中保存的自定义对象类型可能已经不存在，Jackson 反序列化会在登录前失败。
     * 这里只针对该用户的损坏 Session 做一次清理并重试，避免影响其他用户的登录状态。
     */
    private void doLogin(String userId) {
        try {
            StpUtil.login(userId);
        } catch (SaJsonConvertException ex) {
            String sessionKey = StpUtil.getStpLogic().splicingKeySession(userId);
            SaManager.getSaTokenDao().deleteSession(sessionKey);
            log.warn("[登录] 清理无法反序列化的 Sa-Token Session 后重试, userId={}, sessionKey={}", userId, sessionKey);
            StpUtil.login(userId);
        }
    }

    /**
     * 保存登录用户信息到 Sa-Token Session。
     * Session 会持久化到 Redis，因此这里只写入字符串等基础字段，不再保存 UserInfoDTO 这类带包名的对象，
     * 避免后续重构包路径时旧 Redis 数据因为类名不存在而无法反序列化。
     */
    private void saveUserSession(UserDO user) {
        SaSession session = StpUtil.getSession();
        session.set("userId", user.getId());
        session.set("avatar", user.getAvatar());
        session.set("role", user.getRole());
    }


}
