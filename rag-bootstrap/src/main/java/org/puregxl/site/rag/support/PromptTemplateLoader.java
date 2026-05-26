package org.puregxl.site.rag.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PromptTemplateLoader {

    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public String load(String resourcePath) {
        if (StrUtil.isBlank(resourcePath)) {
            throw new IllegalArgumentException("Prompt 资源路径不能为空");
        }
        return cache.computeIfAbsent(resourcePath, this::readTemplate);
    }

    private String readTemplate(String resourcePath) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(resourcePath).getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException ex) {
            throw new IllegalStateException("加载 Prompt 模板失败: " + resourcePath, ex);
        }
    }
}
