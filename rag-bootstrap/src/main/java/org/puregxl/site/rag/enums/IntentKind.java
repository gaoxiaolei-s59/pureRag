package org.puregxl.site.rag.enums;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum IntentKind {
    KB(0),
    MCP(1),
    SYSTEM(2);

    private final int code;


    /**
     * 根据编码获取对应的意图类型
     *
     * @param code 意图类型编码
     * @return 对应的意图类型枚举值，如果编码不存在则返回null
     */
    public static IntentKind fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (IntentKind e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }

    /**
     * 返回枚举名称
     *
     * @return 枚举的名称字符串
     */
    @Override
    public String toString() {
        return name();
    }
}
