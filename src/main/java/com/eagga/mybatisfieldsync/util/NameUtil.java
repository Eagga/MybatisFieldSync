package com.eagga.mybatisfieldsync.util;

/**
 * SQL 生成时使用的命名工具。
 */
public final class NameUtil {
    private NameUtil() {
    }

    /**
     * 将 Java 驼峰字段名转换为下划线列名。
     */
    public static String camelToSnake(String camel) {
        if (camel == null || camel.isEmpty()) {
            return camel;
        }
        StringBuilder sb = new StringBuilder(camel.length() + 8);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
