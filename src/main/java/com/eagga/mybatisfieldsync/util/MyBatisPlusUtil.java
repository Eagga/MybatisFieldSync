package com.eagga.mybatisfieldsync.util;

import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * MyBatis-Plus 注解解析工具。
 */
public final class MyBatisPlusUtil {
    private static final String TABLE_NAME_ANNOTATION = "com.baomidou.mybatisplus.annotation.TableName";
    private static final String TABLE_ID_ANNOTATION = "com.baomidou.mybatisplus.annotation.TableId";

    private MyBatisPlusUtil() {
    }

    public static @NotNull String resolveTableName(@NotNull PsiClass psiClass) {
        String byAnnotation = resolveTableNameFromAnnotation(psiClass);
        if (byAnnotation != null && !byAnnotation.isBlank()) {
            return byAnnotation;
        }
        return NameUtil.camelToSnake(Objects.requireNonNullElse(psiClass.getName(), ""));
    }

    public static @NotNull PrimaryKey resolvePrimaryKey(@NotNull List<FieldInfo> fields) {
        for (FieldInfo field : fields) {
            PsiAnnotation tableId = findAnnotation(field.psiField(), TABLE_ID_ANNOTATION);
            if (tableId == null) {
                continue;
            }
            String columnName = resolveColumnNameFromTableId(field, tableId);
            return new PrimaryKey(field.name(), columnName, field.type(), field.jdbcType());
        }

        for (FieldInfo field : fields) {
            if ("id".equals(field.name())) {
                return new PrimaryKey(field.name(), NameUtil.camelToSnake(field.name()), field.type(), field.jdbcType());
            }
        }

        if (!fields.isEmpty()) {
            FieldInfo first = fields.get(0);
            return new PrimaryKey(first.name(), NameUtil.camelToSnake(first.name()), first.type(), first.jdbcType());
        }

        return new PrimaryKey("id", "id", "Long", "BIGINT");
    }

    private static @Nullable String resolveTableNameFromAnnotation(@NotNull PsiClass psiClass) {
        PsiAnnotation annotation = findAnnotation(psiClass, TABLE_NAME_ANNOTATION);
        if (annotation == null) {
            return null;
        }

        String value = getStringAttribute(annotation, "value");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return getStringAttribute(annotation, "name");
    }

    private static @NotNull String resolveColumnNameFromTableId(@NotNull FieldInfo field, @NotNull PsiAnnotation tableId) {
        String value = getStringAttribute(tableId, "value");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return NameUtil.camelToSnake(field.name());
    }

    private static @Nullable PsiAnnotation findAnnotation(@NotNull PsiClass psiClass, @NotNull String qualifiedName) {
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            if (annotationMatches(annotation, qualifiedName)) {
                return annotation;
            }
        }
        return null;
    }

    private static @Nullable PsiAnnotation findAnnotation(@NotNull PsiField psiField, @NotNull String qualifiedName) {
        for (PsiAnnotation annotation : psiField.getAnnotations()) {
            if (annotationMatches(annotation, qualifiedName)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean annotationMatches(@NotNull PsiAnnotation annotation, @NotNull String qualifiedName) {
        String qn = annotation.getQualifiedName();
        if (qualifiedName.equals(qn)) {
            return true;
        }
        String shortName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        if (shortName.equals(qn)) {
            return true;
        }
        return annotation.getNameReferenceElement() != null
                && shortName.equals(annotation.getNameReferenceElement().getReferenceName());
    }

    private static @Nullable String getStringAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value == null && "value".equals(attributeName)) {
            value = annotation.findDeclaredAttributeValue("value");
        }
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
            return text;
        }
        if (value != null) {
            String text = value.getText();
            if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                return text.substring(1, text.length() - 1);
            }
        }
        return null;
    }

    public record PrimaryKey(
            @NotNull String propertyName,
            @NotNull String columnName,
            @NotNull String javaType,
            @NotNull String jdbcType
    ) {
    }
}
