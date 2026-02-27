package com.eagga.mybatisfieldsync.util;

import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

/**
 * 用于检测字段是否应被忽略的工具类。
 * 支持常见的不持久化/忽略字段注解。
 */
public final class FieldIgnoreUtil {

    /**
     * 需要忽略的注解全限定名集合。
     */
    private static final java.util.Set<String> IGNORE_ANNOTATIONS = java.util.Set.of(
            // JPA 瞬态注解
            "javax.persistence.Transient",
            "jakarta.persistence.Transient",
            // MyBatis-Plus 忽略字段注解
            "com.baomidou.mybatisplus.annotation.TableField",
            // Lombok 相关的瞬态字段（可选）
            "lombok.Transient"
    );

    private FieldIgnoreUtil() {
        // 工具类，阻止实例化
    }

    /**
     * 检查字段是否应被忽略（不参与同步）。
     *
     * @param field PSI 字段
     * @return true 如果字段带有任何忽略注解
     */
    public static boolean shouldIgnore(@NotNull PsiField field) {
        for (String annotationName : IGNORE_ANNOTATIONS) {
            if (hasAnnotation(field, annotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查字段是否包含指定注解。
     */
    private static boolean hasAnnotation(@NotNull PsiField field, @NotNull String annotationQualifiedName) {
        var annotations = field.getAnnotations();
        for (var annotation : annotations) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null && qualifiedName.equals(annotationQualifiedName)) {
                return true;
            }
        }
        return false;
    }
}