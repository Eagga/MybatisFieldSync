package com.eagga.mybatisfieldsync.util;

import org.jetbrains.annotations.NotNull;

public final class XmlMappingRenderUtil {
    private XmlMappingRenderUtil() {
    }

    public static @NotNull String buildParameterPlaceholder(@NotNull String propertyPath,
            @NotNull TypeMappingUtil.ResolvedTypeMapping mapping) {
        StringBuilder builder = new StringBuilder("#{").append(propertyPath);
        if (!mapping.jdbcType().isBlank()) {
            builder.append(",jdbcType=").append(mapping.jdbcType());
        }
        if (mapping.typeHandler() != null && !mapping.typeHandler().isBlank()) {
            builder.append(",typeHandler=").append(mapping.typeHandler());
        }
        return builder.append("}").toString();
    }

    public static @NotNull String buildResultTag(@NotNull String column,
            @NotNull String property,
            @NotNull TypeMappingUtil.ResolvedTypeMapping mapping) {
        StringBuilder builder = new StringBuilder("<result column=\"")
                .append(column)
                .append("\" property=\"")
                .append(property)
                .append("\"");
        if (!mapping.jdbcType().isBlank()) {
            builder.append(" jdbcType=\"").append(mapping.jdbcType()).append("\"");
        }
        if (mapping.typeHandler() != null && !mapping.typeHandler().isBlank()) {
            builder.append(" typeHandler=\"").append(mapping.typeHandler()).append("\"");
        }
        return builder.append("/>").toString();
    }
}
