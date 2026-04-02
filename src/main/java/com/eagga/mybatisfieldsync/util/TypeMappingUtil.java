package com.eagga.mybatisfieldsync.util;

import com.eagga.mybatisfieldsync.settings.MyBatisFieldSyncSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TypeMappingUtil {
    private TypeMappingUtil() {
    }

    public static @NotNull ResolvedTypeMapping resolve(@NotNull String javaType,
            @Nullable String jdbcTypeMappings,
            @Nullable String typeHandlerMappings,
            @NotNull String defaultJdbcType) {
        String jdbcType = resolveJdbcType(javaType, jdbcTypeMappings, defaultJdbcType);
        TypeHandlerMapping handlerMapping = resolveTypeHandler(javaType, typeHandlerMappings);
        if (handlerMapping != null && handlerMapping.jdbcTypeOverride() != null
                && !handlerMapping.jdbcTypeOverride().isBlank()) {
            jdbcType = handlerMapping.jdbcTypeOverride();
        }
        return new ResolvedTypeMapping(jdbcType, handlerMapping == null ? null : handlerMapping.handlerClass());
    }

    public static @NotNull ResolvedTypeMapping resolve(@Nullable MyBatisFieldSyncSettings.State state,
            @NotNull String javaType,
            @NotNull String defaultJdbcType) {
        if (state == null) {
            return new ResolvedTypeMapping(defaultJdbcType, null);
        }
        return resolve(javaType, state.customMappingConfig, state.typeHandlerMappingConfig, defaultJdbcType);
    }

    private static @NotNull String resolveJdbcType(@NotNull String javaType,
            @Nullable String mappingsText,
            @NotNull String defaultJdbcType) {
        if (mappingsText == null || mappingsText.isBlank()) {
            return defaultJdbcType;
        }
        for (String rawLine : mappingsText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            if (javaType.equals(parts[0].trim())) {
                String jdbcType = parts[1].trim();
                return jdbcType.isBlank() ? defaultJdbcType : jdbcType;
            }
        }
        return defaultJdbcType;
    }

    private static @Nullable TypeHandlerMapping resolveTypeHandler(@NotNull String javaType, @Nullable String mappingsText) {
        if (mappingsText == null || mappingsText.isBlank()) {
            return null;
        }
        for (String rawLine : mappingsText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("=", 2);
            if (parts.length != 2 || !javaType.equals(parts[0].trim())) {
                continue;
            }

            String[] segments = parts[1].split(",");
            String handlerClass = segments[0].trim();
            if (handlerClass.isBlank()) {
                return null;
            }

            String jdbcTypeOverride = null;
            for (int i = 1; i < segments.length; i++) {
                String segment = segments[i].trim();
                if (segment.startsWith("jdbcType=")) {
                    String candidate = segment.substring("jdbcType=".length()).trim();
                    if (!candidate.isBlank()) {
                        jdbcTypeOverride = candidate;
                    }
                }
            }
            return new TypeHandlerMapping(handlerClass, jdbcTypeOverride);
        }
        return null;
    }

    private record TypeHandlerMapping(@NotNull String handlerClass, @Nullable String jdbcTypeOverride) {
    }

    public record ResolvedTypeMapping(@NotNull String jdbcType, @Nullable String typeHandler) {
    }
}
