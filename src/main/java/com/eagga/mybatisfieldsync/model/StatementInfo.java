package com.eagga.mybatisfieldsync.model;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * 不可变 Statement DTO，描述一个候选 Mapper 语句。
 */
public record StatementInfo(@NotNull String id,
                            @NotNull String tagName,
                            @NotNull XmlTag tag) {
    @Override
    public String toString() {
        return id + " (" + tagName + ")";
    }
}
