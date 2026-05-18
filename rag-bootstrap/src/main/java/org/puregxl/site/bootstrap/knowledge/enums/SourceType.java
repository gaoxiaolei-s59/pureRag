package org.puregxl.site.bootstrap.knowledge.enums;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SourceType {
    /**
     * 远程URl抓取
     */
    URL("url"),

    /**
     * 文件上传
     */
    FILE("file");
    private final String code;
}
