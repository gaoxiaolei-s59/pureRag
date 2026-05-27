package org.puregxl.site.user.controller;

import org.junit.jupiter.api.Test;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.user.service.AuthService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerTest {

    @Test
    void logoutDelegatesToAuthServiceAndReturnsSuccess() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);

        Result<Void> result = controller.logout();

        verify(authService).logout();
        assertThat(result.isSuccess()).isTrue();
    }
}
