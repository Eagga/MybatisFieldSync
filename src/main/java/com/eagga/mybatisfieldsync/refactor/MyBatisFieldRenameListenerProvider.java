package com.eagga.mybatisfieldsync.refactor;

import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyBatisFieldRenameListenerProvider implements RefactoringElementListenerProvider {
    @Override
    public @Nullable RefactoringElementListener getListener(PsiElement element) {
        if (!(element instanceof PsiField field)) {
            return null;
        }
        PsiClass containingClass = field.getContainingClass();
        String oldName = field.getName();
        if (containingClass == null || oldName == null) {
            return null;
        }
        FieldSyncService service = element.getProject().getService(FieldSyncService.class);
        return new RefactoringElementListener() {
            @Override
            public void elementMoved(@NotNull PsiElement newElement) {
            }

            @Override
            public void elementRenamed(@NotNull PsiElement newElement) {
                if (newElement instanceof PsiField renamedField) {
                    String newName = renamedField.getName();
                    if (newName != null) {
                        service.renameFieldReferences(containingClass, oldName, newName);
                    }
                }
            }
        };
    }
}
