package org.puregxl.site.knowledge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentStatus {
    /**
     * 文档待处理
     */
    PENDING("pending"),

    /**
     * 文档处理中
     */
    RUNNING("running"),

    /**
     * 文档处理失败
     */
    FAILED("failed"),

    /**
     * 文档处理成功
     */
    SUCCESS("success");


    private final String code;
}
