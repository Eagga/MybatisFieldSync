package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.EntitySyncResult;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 字段同步功能的编辑器右键入口动作。
 * <p>
 * 仅在 Java 编辑器右键菜单且处于类级上下文时显示。
 */
public class SyncFieldsAction extends AnAction implements DumbAware {
    public SyncFieldsAction() {
        getTemplatePresentation().setText(MyBatisFieldSyncBundle.message("action.syncFields.text"));
        getTemplatePresentation().setDescription(MyBatisFieldSyncBundle.message("action.syncFields.description"));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        boolean visible = project != null
                && psiFile instanceof PsiJavaFile
                && resolveTargetClass(e) != null;

        presentation.setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiClass targetClass = resolveTargetClass(e);
        if (targetClass == null || targetClass.getName() == null) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.noJavaClass"));
            return;
        }

        FieldSyncService service = project.getService(FieldSyncService.class);
        EntitySyncResult result = SyncFieldsWorkflow.run(project, service, targetClass);
        handleResult(project, result);
    }

    /**
     * 从当前光标上下文解析目标类，解析失败时回退到当前文件中的第一个类。
     */
    private PsiClass resolveTargetClass(AnActionEvent e) {
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement != null) {
            PsiClass around = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
            if (around != null) {
                return around;
            }
        }

        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (file instanceof PsiJavaFile javaFile && javaFile.getClasses().length > 0) {
            return javaFile.getClasses()[0];
        }
        return null;
    }

    private void handleResult(@NotNull Project project, @NotNull EntitySyncResult result) {
        switch (result.status()) {
            case SUCCESS -> NotificationUtil.info(project, buildSuccessMessage(result));
            case PARTIAL_SUCCESS -> {
                NotificationUtil.info(project, buildSuccessMessage(result));
                NotificationUtil.error(project,
                        MyBatisFieldSyncBundle.message("notify.sync.partialFailed",
                                String.join("; ", result.failedStatements())));
            }
            case FAILED -> NotificationUtil.error(project, result.detailMessage());
            case SKIPPED -> {
            }
        }
    }

    private @NotNull String buildSuccessMessage(@NotNull EntitySyncResult result) {
        if (result.successCount() == 1) {
            return MyBatisFieldSyncBundle.message("notify.sync.success",
                    result.selectedFieldCount(),
                    result.successStatementIds().get(0));
        }
        return MyBatisFieldSyncBundle.message("notify.sync.success.multi",
                result.selectedFieldCount(),
                result.successCount(),
                String.join(", ", result.successStatementIds()));
    }
}
