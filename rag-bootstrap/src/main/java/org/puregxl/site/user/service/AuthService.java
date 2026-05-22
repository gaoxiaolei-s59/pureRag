package org.puregxl.site.user.service;

import org.puregxl.site.user.dto.request.LoginRequest;
import org.puregxl.site.user.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
