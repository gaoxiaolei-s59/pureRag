package org.puregxl.site.bootstrap.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.bootstrap.user.context.UserInfoDto;
import org.puregxl.site.bootstrap.user.dao.entity.UserDO;
import org.puregxl.site.bootstrap.user.dao.mapper.UserMapper;
import org.puregxl.site.bootstrap.user.dto.request.LoginRequest;
import org.puregxl.site.bootstrap.user.dto.response.LoginResponse;
import org.puregxl.site.bootstrap.user.service.AuthService;
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

        StpUtil.login(user.getId());

        UserInfoDto userInfoDto = new UserInfoDto();
        userInfoDto.setUserId(user.getId());
        userInfoDto.setAvatar(user.getAvatar());
        userInfoDto.setRole(user.getRole());
        StpUtil.getSession().set("user", userInfoDto);

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



}
