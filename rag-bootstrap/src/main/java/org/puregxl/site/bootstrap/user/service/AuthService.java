package org.puregxl.site.bootstrap.user.service;

import org.puregxl.site.bootstrap.user.dto.request.LoginRequest;
import org.puregxl.site.bootstrap.user.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
