package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.service.MapperInterfaceService;
import com.eagga.mybatisfieldsync.ui.MapperMethodDialog;
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

import java.util.List;

/**
 * 生成 Mapper 接口方法的 Action
 */
public class GenerateMapperMethodsAction extends AnAction implements DumbAware {

    public GenerateMapperMethodsAction() {
        getTemplatePresentation().setText("Generate Mapper Methods");
        getTemplatePresentation().setDescription("Generate methods in Mapper interface");
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

        MapperInterfaceService mapperService = project.getService(MapperInterfaceService.class);
        PsiClass mapperInterface = mapperService.findMapperInterface(targetClass);

        if (mapperInterface == null) {
            NotificationUtil.error(project, "Mapper interface not found for " + targetClass.getName());
            return;
        }

        FieldSyncService fieldService = project.getService(FieldSyncService.class);
        List<FieldInfo> allFields = fieldService.collectFields(targetClass, true);

        MapperMethodDialog dialog = new MapperMethodDialog(project, targetClass, mapperInterface);
        if (!dialog.showAndGet()) {
            return;
        }

        try {
            mapperService.generateMapperMethods(mapperInterface, targetClass, allFields, dialog.getSelectedMethods());
            NotificationUtil.info(project, "Mapper methods generated successfully in " + mapperInterface.getName());
        } catch (Exception ex) {
            NotificationUtil.error(project, "Failed to generate methods: " + ex.getMessage());
        }
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
