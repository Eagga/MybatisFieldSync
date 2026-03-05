package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.service.CrudTemplateService;
import com.eagga.mybatisfieldsync.ui.CrudTemplateDialog;
import com.eagga.mybatisfieldsync.util.NotificationUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GenerateCrudAction extends AnAction implements DumbAware {
    public GenerateCrudAction() {
        getTemplatePresentation().setText("Generate MyBatis CRUD Template");
        getTemplatePresentation().setDescription("Generate standard CRUD statements for entity");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        boolean visible = project != null
                && psiFile instanceof PsiJavaFile
                && isAllowedPosition(e.getData(CommonDataKeys.PSI_ELEMENT));

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

        FieldSyncService fieldService = project.getService(FieldSyncService.class);
        List<XmlFile> xmlFiles = fieldService.findCandidateXmlFiles(targetClass);
        if (xmlFiles.isEmpty()) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.noXml", targetClass.getName()));
            return;
        }

        List<FieldInfo> allFields = fieldService.collectFields(targetClass, true);
        if (allFields.isEmpty()) {
            NotificationUtil.error(project, "No fields found in class");
            return;
        }

        CrudTemplateDialog dialog = new CrudTemplateDialog(project, targetClass, xmlFiles.get(0), allFields);
        if (!dialog.showAndGet()) {
            return;
        }

        CrudTemplateService templateService = project.getService(CrudTemplateService.class);
        try {
            templateService.generateCrudStatements(xmlFiles.get(0), targetClass, allFields, dialog.getSelectedTemplates());
            NotificationUtil.info(project, "CRUD templates generated successfully");
        } catch (Exception ex) {
            NotificationUtil.error(project, "Failed to generate CRUD: " + ex.getMessage());
        }
    }

    private boolean isAllowedPosition(PsiElement element) {
        if (element == null || element instanceof PsiWhiteSpace) {
            return true;
        }
        if (element instanceof PsiClass) {
            return true;
        }
        if (element instanceof PsiIdentifier identifier && identifier.getParent() instanceof PsiClass psiClass) {
            return identifier.equals(psiClass.getNameIdentifier());
        }
        return false;
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
