package com.eagga.mybatisfieldsync.util;

import com.eagga.mybatisfieldsync.settings.MyBatisFieldSyncSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class XmlFormatSettingsUtil {
    private XmlFormatSettingsUtil() {
    }

    public static @NotNull ResolvedXmlFormat resolve(@Nullable MyBatisFieldSyncSettings.State state,
            @Nullable String body) {
        if (state == null) {
            return resolve(body, "AUTO", "AUTO", "AUTO");
        }
        return resolve(body, state.xmlIndentStyle, state.xmlLineBreakStyle, state.xmlCommaStyle);
    }

    public static @NotNull ResolvedXmlFormat resolve(@Nullable String body,
            @Nullable String indentSetting,
            @Nullable String lineBreakSetting,
            @Nullable String commaSetting) {
        String safeBody = body == null ? "" : body;
        String indentUnit = resolveIndentUnit(safeBody, indentSetting);
        LineBreakStyle lineBreakStyle = resolveLineBreakStyle(safeBody, lineBreakSetting);
        CommaStyle commaStyle = resolveCommaStyle(safeBody, commaSetting);
        return new ResolvedXmlFormat(indentUnit, lineBreakStyle, commaStyle);
    }

    public static @NotNull String normalizeEntry(@NotNull String entry) {
        String normalized = entry.trim();
        if (normalized.startsWith(",")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.endsWith(",")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    public static @NotNull String renderEntry(@NotNull String entry,
            @NotNull ResolvedXmlFormat format,
            boolean firstEntry) {
        String normalized = normalizeEntry(entry);
        if (format.lineBreakStyle() == LineBreakStyle.SINGLE_LINE) {
            return normalized;
        }
        if (format.commaStyle() == CommaStyle.LEADING) {
            return firstEntry ? normalized : ", " + normalized;
        }
        return normalized + ",";
    }

    private static @NotNull String resolveIndentUnit(@NotNull String body, @Nullable String indentSetting) {
        if (!isAuto(indentSetting)) {
            return switch (normalizeSetting(indentSetting)) {
                case "TAB" -> "\t";
                case "SPACE_2" -> "  ";
                default -> "    ";
            };
        }
        if (!body.isBlank()) {
            return IndentUtil.detectIndentUnit(body);
        }
        return "    ";
    }

    private static @NotNull LineBreakStyle resolveLineBreakStyle(@NotNull String body, @Nullable String lineBreakSetting) {
        if (!isAuto(lineBreakSetting)) {
            return "SINGLE_LINE".equals(normalizeSetting(lineBreakSetting))
                    ? LineBreakStyle.SINGLE_LINE
                    : LineBreakStyle.MULTI_LINE;
        }
        return hasMultipleNonBlankLines(body) ? LineBreakStyle.MULTI_LINE : LineBreakStyle.SINGLE_LINE;
    }

    private static @NotNull CommaStyle resolveCommaStyle(@NotNull String body, @Nullable String commaSetting) {
        if (!isAuto(commaSetting)) {
            return "LEADING".equals(normalizeSetting(commaSetting))
                    ? CommaStyle.LEADING
                    : CommaStyle.TRAILING;
        }
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(",")) {
                return CommaStyle.LEADING;
            }
            if (trimmed.endsWith(",")) {
                return CommaStyle.TRAILING;
            }
        }
        return CommaStyle.TRAILING;
    }

    private static boolean hasMultipleNonBlankLines(@NotNull String body) {
        int count = 0;
        for (String line : body.split("\\R")) {
            if (!line.isBlank()) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAuto(@Nullable String setting) {
        return setting == null || setting.isBlank() || "AUTO".equals(normalizeSetting(setting));
    }

    private static @NotNull String normalizeSetting(@Nullable String setting) {
        return setting == null ? "AUTO" : setting.trim().toUpperCase();
    }

    public enum LineBreakStyle {
        SINGLE_LINE,
        MULTI_LINE
    }

    public enum CommaStyle {
        TRAILING,
        LEADING
    }

    public record ResolvedXmlFormat(@NotNull String indentUnit,
                                    @NotNull LineBreakStyle lineBreakStyle,
                                    @NotNull CommaStyle commaStyle) {
    }
}
