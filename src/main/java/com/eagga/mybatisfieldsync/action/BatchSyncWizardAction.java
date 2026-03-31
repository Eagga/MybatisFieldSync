package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.EntitySyncResult;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.ui.BatchSyncWizardDialog;
import com.eagga.mybatisfieldsync.ui.TextReportDialog;
import com.eagga.mybatisfieldsync.util.NotificationUtil;
import com.intellij.ide.highlighter.JavaFileType;
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
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 批量同步向导：多实体选择，逐项进入预览执行，末尾汇总报告。
 */
public class BatchSyncWizardAction extends AnAction implements DumbAware {
    public BatchSyncWizardAction() {
        getTemplatePresentation().setText(MyBatisFieldSyncBundle.message("action.batchSync.text"));
        getTemplatePresentation().setDescription(MyBatisFieldSyncBundle.message("action.batchSync.description"));
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

        PsiClass currentClass = resolveTargetClass(e);
        if (currentClass == null) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.noJavaClass"));
            return;
        }

        List<PsiClass> entityCandidates = collectEntityCandidates(project, currentClass);
        if (entityCandidates.isEmpty()) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.noJavaClass"));
            return;
        }

        BatchSyncWizardDialog dialog = new BatchSyncWizardDialog(project, entityCandidates, currentClass);
        if (!dialog.showAndGet()) {
            return;
        }

        List<PsiClass> selectedEntities = dialog.getSelectedEntities();
        if (selectedEntities.isEmpty()) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.batch.noEntity"));
            return;
        }

        FieldSyncService service = project.getService(FieldSyncService.class);
        List<EntitySyncResult> results = new ArrayList<>();
        for (PsiClass entityClass : selectedEntities) {
            results.add(SyncFieldsWorkflow.run(project, service, entityClass));
        }

        NotificationUtil.info(project, buildSummaryNotification(selectedEntities.size(), results));
        new TextReportDialog(project,
                MyBatisFieldSyncBundle.message("dialog.report.batch.title"),
                buildSummaryReport(selectedEntities.size(), results)).show();
    }

    private @NotNull List<PsiClass> collectEntityCandidates(@NotNull Project project, @NotNull PsiClass currentClass) {
        List<PsiClass> classes = new ArrayList<>();
        FileTypeIndex.processFiles(JavaFileType.INSTANCE, virtualFile -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile instanceof PsiJavaFile javaFile) {
                for (PsiClass psiClass : javaFile.getClasses()) {
                    if (isEntityCandidate(psiClass)) {
                        classes.add(psiClass);
                    }
                }
            }
            return true;
        }, GlobalSearchScope.projectScope(project));

        if (isEntityCandidate(currentClass) && classes.stream().noneMatch(existing -> sameClass(existing, currentClass))) {
            classes.add(currentClass);
        }

        classes.sort(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER));
        return classes;
    }

    private boolean isEntityCandidate(@NotNull PsiClass psiClass) {
        return psiClass.getQualifiedName() != null
                && psiClass.getName() != null
                && !psiClass.isInterface()
                && !psiClass.isEnum()
                && !psiClass.isAnnotationType();
    }

    private boolean sameClass(@NotNull PsiClass left, @NotNull PsiClass right) {
        return displayName(left).equals(displayName(right));
    }

    private String displayName(@NotNull PsiClass psiClass) {
        return psiClass.getQualifiedName() == null ? psiClass.getName() : psiClass.getQualifiedName();
    }

    private @NotNull String buildSummaryNotification(int selectedCount, @NotNull List<EntitySyncResult> results) {
        int success = count(results, EntitySyncResult.Status.SUCCESS);
        int partial = count(results, EntitySyncResult.Status.PARTIAL_SUCCESS);
        int failed = count(results, EntitySyncResult.Status.FAILED);
        int skipped = count(results, EntitySyncResult.Status.SKIPPED);
        return MyBatisFieldSyncBundle.message("notify.batch.completed", selectedCount, success, partial, failed, skipped);
    }

    private @NotNull String buildSummaryReport(int selectedCount, @NotNull List<EntitySyncResult> results) {
        int success = count(results, EntitySyncResult.Status.SUCCESS);
        int partial = count(results, EntitySyncResult.Status.PARTIAL_SUCCESS);
        int failed = count(results, EntitySyncResult.Status.FAILED);
        int skipped = count(results, EntitySyncResult.Status.SKIPPED);

        StringBuilder report = new StringBuilder();
        report.append(MyBatisFieldSyncBundle.message("report.batch.header",
                selectedCount,
                success,
                partial,
                failed,
                skipped));

        for (EntitySyncResult result : results) {
            report.append("\n\n");
            switch (result.status()) {
                case SUCCESS -> report.append(MyBatisFieldSyncBundle.message("report.batch.item.success",
                        result.entityName(),
                        result.xmlPath(),
                        result.selectedFieldCount(),
                        String.join(", ", result.successStatementIds())));
                case PARTIAL_SUCCESS -> report.append(MyBatisFieldSyncBundle.message("report.batch.item.partial",
                        result.entityName(),
                        result.xmlPath(),
                        String.join(", ", result.successStatementIds()),
                        String.join("; ", result.failedStatements())));
                case FAILED -> report.append(MyBatisFieldSyncBundle.message("report.batch.item.failed",
                        result.entityName(),
                        result.detailMessage()));
                case SKIPPED -> report.append(MyBatisFieldSyncBundle.message("report.batch.item.skipped",
                        result.entityName(),
                        result.detailMessage()));
            }
        }
        return report.toString();
    }

    private int count(@NotNull List<EntitySyncResult> results, @NotNull EntitySyncResult.Status status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }

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
}
