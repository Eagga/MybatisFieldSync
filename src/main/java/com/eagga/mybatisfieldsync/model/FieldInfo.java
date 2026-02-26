package com.eagga.mybatisfieldsync.model;

import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

/**
 * 不可变字段 DTO，描述一个可被勾选同步的 Java 字段。
 */
public record FieldInfo(@NotNull PsiField psiField,
                        @NotNull String name,
                        @NotNull String type,
                        @NotNull String jdbcType,
                        @NotNull String ownerClass,
                        boolean inherited) {
}
