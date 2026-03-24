package com.eagga.mybatisfieldsync.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XmlFieldSyncSupport {
    private static final Pattern RESULT_TAG_PATTERN = Pattern.compile("<(id|result)\\b([^>]*)/?>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_COMMENT_LINE_PATTERN = Pattern.compile("^(\\s*)<!--.*-->\\s*$");
    private static final Map<String, String> JDBC_TO_JAVA = Map.ofEntries(
            Map.entry("VARCHAR", "String"),
            Map.entry("CHAR", "String"),
            Map.entry("LONGVARCHAR", "String"),
            Map.entry("CLOB", "String"),
            Map.entry("INTEGER", "Integer"),
            Map.entry("INT", "Integer"),
            Map.entry("SMALLINT", "Short"),
            Map.entry("TINYINT", "Byte"),
            Map.entry("BIGINT", "Long"),
            Map.entry("BIT", "Boolean"),
            Map.entry("BOOLEAN", "Boolean"),
            Map.entry("FLOAT", "Float"),
            Map.entry("DOUBLE", "Double"),
            Map.entry("REAL", "Double"),
            Map.entry("DECIMAL", "java.math.BigDecimal"),
            Map.entry("NUMERIC", "java.math.BigDecimal"),
            Map.entry("DATE", "java.time.LocalDate"),
            Map.entry("TIME", "java.time.LocalTime"),
            Map.entry("TIMESTAMP", "java.time.LocalDateTime"),
            Map.entry("BLOB", "byte[]"),
            Map.entry("BINARY", "byte[]"),
            Map.entry("VARBINARY", "byte[]"));

    private XmlFieldSyncSupport() {
    }

    public static @NotNull List<XmlFieldDraft> parseResultMap(@NotNull String fragment) {
        List<XmlFieldDraft> drafts = new ArrayList<>();
        Matcher matcher = RESULT_TAG_PATTERN.matcher(fragment);
        while (matcher.find()) {
            String tagText = matcher.group();
            String property = attr(tagText, "property");
            if (property == null || property.isBlank()) {
                continue;
            }
            String column = attr(tagText, "column");
            String jdbcType = attr(tagText, "jdbcType");
            drafts.add(new XmlFieldDraft(property, column, jdbcType, null));
        }
        return dedupeByFieldName(drafts);
    }

    public static @NotNull List<XmlFieldDraft> parseBaseColumnList(@NotNull String fragment) {
        String cleaned = fragment
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        String[] rawColumns = cleaned.split(",");
        List<XmlFieldDraft> drafts = new ArrayList<>();
        for (String rawColumn : rawColumns) {
            String column = rawColumn
                    .replaceAll("[\\r\\n\\t]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (column.isEmpty()) {
                continue;
            }
            column = column.replace("`", "").replace("\"", "");
            if (!column.matches("[A-Za-z0-9_$.]+")) {
                continue;
            }
            drafts.add(new XmlFieldDraft(snakeToCamel(column), column, null, null));
        }
        return dedupeByFieldName(drafts);
    }

    public static @NotNull String toJavaFieldSnippet(@NotNull XmlFieldDraft draft) {
        StringBuilder builder = new StringBuilder();
        if (draft.comment() != null && !draft.comment().isBlank()) {
            builder.append("/**\n")
                    .append(" * ")
                    .append(draft.comment().trim())
                    .append("\n")
                    .append(" */\n");
        }
        builder.append("private ")
                .append(resolveJavaType(draft.jdbcType()))
                .append(" ")
                .append(draft.fieldName())
                .append(";");
        return builder.toString();
    }

    public static @NotNull String buildReversePreview(@NotNull List<XmlFieldDraft> drafts,
            @NotNull List<String> conflicts,
            @NotNull String entityName) {
        StringBuilder builder = new StringBuilder();
        builder.append("Entity: ").append(entityName).append("\n\n");
        if (drafts.isEmpty()) {
            builder.append("No new fields can be generated from the selected XML fragment.\n");
        } else {
            for (XmlFieldDraft draft : drafts) {
                builder.append(toJavaFieldSnippet(draft)).append("\n\n");
            }
        }
        if (!conflicts.isEmpty()) {
            builder.append("Conflicts (skipped): ").append(String.join(", ", conflicts)).append("\n");
        }
        return builder.toString().stripTrailing() + "\n";
    }

    public static @NotNull String renamePlaceholders(@NotNull String text, @NotNull String oldName,
            @NotNull String newName) {
        return renameTokenInExpressions(text, oldName, newName, "#\\{([^}]*)}", "#");
    }

    public static @NotNull String renameDollarPlaceholders(@NotNull String text, @NotNull String oldName,
            @NotNull String newName) {
        return renameTokenInExpressions(text, oldName, newName, "\\$\\{([^}]*)}", "$");
    }

    public static @NotNull String renameTestExpression(@NotNull String text, @NotNull String oldName,
            @NotNull String newName) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b");
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(newName));
    }

    public static @NotNull String upsertCommentBeforeLine(@NotNull String body,
            @NotNull Pattern targetPattern,
            @NotNull String comment) {
        String normalizedComment = sanitizeXmlComment(comment);
        if (normalizedComment.isBlank()) {
            return body;
        }
        String[] lines = body.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!targetPattern.matcher(lines[i]).find()) {
                continue;
            }
            String indent = detectIndent(lines[i]);
            String commentLine = indent + "<!-- " + normalizedComment + " -->";
            if (i > 0) {
                Matcher matcher = XML_COMMENT_LINE_PATTERN.matcher(lines[i - 1]);
                if (matcher.matches() && Objects.equals(matcher.group(1), indent)) {
                    lines[i - 1] = commentLine;
                    return String.join("\n", lines);
                }
            }
            List<String> updated = new ArrayList<>(List.of(lines));
            updated.add(i, commentLine);
            return String.join("\n", updated);
        }
        return body;
    }

    public static @NotNull String sanitizeXmlComment(@Nullable String comment) {
        if (comment == null) {
            return "";
        }
        return comment.replace("--", "- -").replaceAll("\\s+", " ").trim();
    }

    public static @NotNull String extractFieldComment(@Nullable String rawComment) {
        if (rawComment == null || rawComment.isBlank()) {
            return "";
        }
        String text = rawComment
                .replaceAll("(?s)^/\\*\\*", "")
                .replaceAll("(?s)^/\\*", "")
                .replaceAll("(?s)\\*/$", "");
        String[] lines = text.split("\\n");
        List<String> parts = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.replaceFirst("^\\s*\\*\\s?", "").trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return String.join(" ", parts).trim();
    }

    public static @NotNull String snakeToCamel(@NotNull String column) {
        String lower = column.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean upperNext = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static @NotNull String resolveJavaType(@Nullable String jdbcType) {
        if (jdbcType == null || jdbcType.isBlank()) {
            return "String";
        }
        return JDBC_TO_JAVA.getOrDefault(jdbcType.toUpperCase(Locale.ROOT), "String");
    }

    private static @NotNull String renameTokenInExpressions(@NotNull String text,
            @NotNull String oldName,
            @NotNull String newName,
            @NotNull String patternText,
            @NotNull String marker) {
        Pattern pattern = Pattern.compile(patternText);
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String expr = matcher.group(1);
            String updatedExpr = renameFieldPath(expr, oldName, newName);
            String replacement = marker.equals("$") ? "${" + updatedExpr + "}" : "#{" + updatedExpr + "}";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static @NotNull String renameFieldPath(@NotNull String expr, @NotNull String oldName,
            @NotNull String newName) {
        Pattern fieldPathPattern = Pattern.compile("(^|[^A-Za-z0-9_])((?:[A-Za-z0-9_]+\\.)?)" + Pattern.quote(oldName)
                + "(\\b)");
        Matcher matcher = fieldPathPattern.matcher(expr);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + matcher.group(2) + newName + matcher.group(3);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static @Nullable String attr(@NotNull String tagText, @NotNull String attrName) {
        Matcher matcher = Pattern.compile(attrName + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE)
                .matcher(tagText);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static @NotNull String detectIndent(@NotNull String line) {
        int index = 0;
        while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
            index++;
        }
        return line.substring(0, index);
    }

    private static @NotNull List<XmlFieldDraft> dedupeByFieldName(@NotNull List<XmlFieldDraft> drafts) {
        LinkedHashMap<String, XmlFieldDraft> unique = new LinkedHashMap<>();
        for (XmlFieldDraft draft : drafts) {
            unique.putIfAbsent(draft.fieldName(), draft);
        }
        return new ArrayList<>(unique.values());
    }

    public record XmlFieldDraft(@NotNull String fieldName,
                                @Nullable String columnName,
                                @Nullable String jdbcType,
                                @Nullable String comment) {
    }
}
