package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.model.StatementInfo;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.ui.FieldSelectionDialog;
import com.eagga.mybatisfieldsync.ui.PreviewDialog;
import com.eagga.mybatisfieldsync.util.NotificationUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SyncFieldCommentsToXmlAction extends AnAction implements DumbAware {
    public SyncFieldCommentsToXmlAction() {
        getTemplatePresentation().setText("Sync Field Comments to XML");
        getTemplatePresentation().setDescription("Sync Java field comments to MyBatis XML comments");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(project != null
                && psiFile instanceof PsiJavaFile
                && resolveTargetClass(e) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        PsiClass targetClass = resolveTargetClass(e);
        if (targetClass == null || targetClass.getName() == null) {
            NotificationUtil.error(project, "No target Java class found.");
            return;
        }

        FieldSyncService service = project.getService(FieldSyncService.class);
        List<XmlFile> xmlFiles = service.findCandidateXmlFiles(targetClass);
        if (xmlFiles.isEmpty()) {
            NotificationUtil.error(project, "No matching MyBatis XML found for class: " + targetClass.getName());
            return;
        }

        FieldSelectionDialog dialog = new FieldSelectionDialog(project, service, targetClass, xmlFiles);
        dialog.setTitle("Sync Field Comments to XML");
        if (!dialog.showAndGet()) {
            return;
        }

        List<FieldInfo> selectedFields = dialog.getSelectedFields();
        XmlFile xmlFile = dialog.getSelectedXmlFile();
        List<StatementInfo> statements = dialog.getSelectedStatements();
        if (selectedFields.isEmpty() || xmlFile == null || statements.isEmpty()) {
            NotificationUtil.error(project, "Please select fields and target statements.");
            return;
        }

        XmlFile previewFile = (XmlFile) PsiFileFactory.getInstance(project)
                .createFileFromText(xmlFile.getName(), xmlFile.getFileType(), xmlFile.getText());
        for (StatementInfo statement : statements) {
            XmlTag mockTag = findEquivalentTag(previewFile, statement.tag());
            if (mockTag != null) {
                service.syncCommentsInWriteCommand(previewFile,
                        new StatementInfo(statement.id(), statement.tagName(), mockTag),
                        selectedFields);
            }
        }

        PreviewDialog previewDialog = new PreviewDialog(project,
                "Preview Comment Synchronization",
                "Sync Comments",
                "Cancel",
                previewFile.getText());
        if (!previewDialog.showAndGet()) {
            return;
        }

        for (StatementInfo statement : statements) {
            service.syncCommentsInWriteCommand(xmlFile, statement, selectedFields);
        }

        NotificationUtil.info(project, "Synchronized comments for " + selectedFields.size() + " field(s).");
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

    private XmlTag findEquivalentTag(XmlFile file, XmlTag original) {
        if (file.getRootTag() == null) {
            return null;
        }
        for (XmlTag child : file.getRootTag().getSubTags()) {
            if (original.getName().equals(child.getName())
                    && original.getAttributeValue("id") != null
                    && original.getAttributeValue("id").equals(child.getAttributeValue("id"))) {
                return child;
            }
        }
        return null;
    }
}
