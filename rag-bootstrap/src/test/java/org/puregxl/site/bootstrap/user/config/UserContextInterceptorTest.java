package org.puregxl.site.bootstrap.user.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextInterceptorTest {

    @Test
    void preHandleSkipsLoginCheckForAsyncAndErrorDispatch() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        HttpServletResponse response = mock(HttpServletResponse.class);

        HttpServletRequest asyncRequest = mock(HttpServletRequest.class);
        when(asyncRequest.getDispatcherType()).thenReturn(DispatcherType.ASYNC);
        assertThatCode(() -> assertThat(interceptor.preHandle(asyncRequest, response, new Object())).isTrue())
                .doesNotThrowAnyException();

        HttpServletRequest errorRequest = mock(HttpServletRequest.class);
        when(errorRequest.getDispatcherType()).thenReturn(DispatcherType.ERROR);
        assertThatCode(() -> assertThat(interceptor.preHandle(errorRequest, response, new Object())).isTrue())
                .doesNotThrowAnyException();
    }
}
