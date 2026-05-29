package org.puregxl.site.rag.enums;

/**
 * 检索的类别
 */
public enum SearchChannelType {

    /**
     * 向量检索
     */
    VECTOR("vector", "向量检索"),

    /**
     * 全局检索
     */
    GLOBAL("global", "全局检索");

    private final String code;

    private final String desc;

    SearchChannelType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static SearchChannelType of(String code) {
        for (SearchChannelType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown search channel type: " + code);
    }
}