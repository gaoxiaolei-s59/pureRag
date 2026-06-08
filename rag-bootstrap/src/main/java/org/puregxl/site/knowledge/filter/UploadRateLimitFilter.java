package org.puregxl.site.knowledge.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.knowledge.config.RagSemaphoreProperties;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadRateLimitFilter extends OncePerRequestFilter {
    private final RedissonClient redissonClient;
    private final RagSemaphoreProperties semaphoreProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!isUploadRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取信号量配置
        RagSemaphoreProperties.PermitExpirableConfig config = semaphoreProperties.getDocumentUpload();
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(config.getName());

        String permitId = null;
        try {
            permitId = semaphore.tryAcquire(
                    config.getMaxWaitSeconds(),
                    config.getLeaseSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.error("[上传限流] 获取上传许可失败", e);
            response.setStatus(500);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"500\",\"message\":\"获取上传许可失败\"}");
            return;
        }

        if (permitId == null) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"429\",\"message\":\"当前上传人数过多，请稍后再试\"}");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            boolean released = semaphore.tryRelease(permitId);
            if (!released) {
                log.warn("许可不存在或者已经被释放, permitId = {}", permitId);
            }
        }
    }


    private static final String UPLOAD_PATH_PATTERN = "/knowledge-base/";
    private static final String UPLOAD_PATH_SUFFIX = "/docs/upload";

    /**
     * 判断请求是否为文档上传
     * @param request
     * @return
     */
    private boolean isUploadRequest(HttpServletRequest request) {
        if (!Objects.equals(request.getMethod(), "POST")) {
            return false;
        }
        String requestURI = request.getRequestURI();
        return requestURI != null && requestURI.contains(UPLOAD_PATH_PATTERN) && requestURI.endsWith(UPLOAD_PATH_SUFFIX);

    }
}
