package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.model.StatementInfo;
import com.eagga.mybatisfieldsync.model.SyncException;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.ui.FieldSelectionDialog;
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

import java.util.ArrayList;
import java.util.List;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlTag;
import com.eagga.mybatisfieldsync.ui.PreviewDialog;
import com.intellij.openapi.application.ApplicationManager;

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
                && ActionPlaces.EDITOR_POPUP.equals(e.getPlace())
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

        FieldSyncService service = project.getService(FieldSyncService.class);
        List<XmlFile> xmlFiles = service.findCandidateXmlFiles(targetClass);
        if (xmlFiles.isEmpty()) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.noXml", targetClass.getName()));
            return;
        }

        FieldSelectionDialog dialog = new FieldSelectionDialog(project, service, targetClass, xmlFiles);
        if (!dialog.showAndGet()) {
            return;
        }

        List<FieldInfo> selectedFields = dialog.getSelectedFields();
        if (selectedFields.isEmpty()) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.noField"));
            return;
        }
        List<FieldInfo> allFieldsInOrder = dialog.getAllFieldsInOrder();

        XmlFile xmlFile = dialog.getSelectedXmlFile();
        List<StatementInfo> statements = dialog.getSelectedStatements();
        if (xmlFile == null || statements.isEmpty()) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.noStatement"));
            return;
        }

        // Preview feature
        XmlFile copyFile = (XmlFile) PsiFileFactory.getInstance(project).createFileFromText(
                xmlFile.getName(), xmlFile.getFileType(), xmlFile.getText());

        List<String> preFailed = new ArrayList<>();
        ApplicationManager.getApplication().runWriteAction(() -> {
            for (StatementInfo stmt : statements) {
                try {
                    XmlTag mockTag = findEquivalentTag(copyFile, stmt.tag());
                    if (mockTag != null) {
                        StatementInfo mockStmt = new StatementInfo(stmt.id(), stmt.tagName(), mockTag);
                        service.syncInWriteCommand(copyFile, mockStmt, selectedFields, allFieldsInOrder, targetClass.getName());
                    }
                } catch (Exception ex) {
                    preFailed.add(stmt.id() + ": " + ex.getMessage());
                }
            }
        });

        if (!preFailed.isEmpty() && preFailed.size() == statements.size()) {
            NotificationUtil.error(project,
                    MyBatisFieldSyncBundle.message("notify.sync.partialFailed", String.join("; ", preFailed)));
            return;
        }

        PreviewDialog previewDialog = new PreviewDialog(project, copyFile.getText());
        if (!previewDialog.showAndGet()) {
            return;
        }

        // Execute actual
        List<String> failedStatements = new ArrayList<>();
        List<String> successStatementIds = new ArrayList<>();
        int successCount = 0;
        for (StatementInfo statement : statements) {
            try {
                service.syncInWriteCommand(xmlFile, statement, selectedFields, allFieldsInOrder, targetClass.getName());
                successCount++;
                successStatementIds.add(statement.id());
            } catch (SyncException ex) {
                failedStatements.add(statement.id() + ": " + ex.getMessage());
            }
        }

        if (successCount > 0) {
            if (successCount == 1) {
                NotificationUtil.info(project,
                        MyBatisFieldSyncBundle.message("notify.sync.success", selectedFields.size(),
                                successStatementIds.get(0)));
            } else {
                String statementNames = String.join(", ", successStatementIds);
                NotificationUtil.info(project,
                        MyBatisFieldSyncBundle.message("notify.sync.success.multi", selectedFields.size(), successCount,
                                statementNames));
            }
        }

        if (!failedStatements.isEmpty()) {
            NotificationUtil.error(project,
                    MyBatisFieldSyncBundle.message("notify.sync.partialFailed", String.join("; ", failedStatements)));
        }
    }

    /**
     * 仅允许在类名或空白区域右键时显示动作。
     */
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

    private XmlTag findEquivalentTag(XmlFile file, XmlTag original) {
        if (file.getRootTag() == null)
            return null;
        for (XmlTag child : file.getRootTag().getSubTags()) {
            if (original.getName().equals(child.getName()) &&
                    original.getAttributeValue("id") != null &&
                    original.getAttributeValue("id").equals(child.getAttributeValue("id"))) {
                return child;
            }
        }
        return null;
    }
}
