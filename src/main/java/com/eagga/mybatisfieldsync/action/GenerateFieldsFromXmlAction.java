package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.ui.PreviewDialog;
import com.eagga.mybatisfieldsync.util.NotificationUtil;
import com.eagga.mybatisfieldsync.util.XmlFieldSyncSupport;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class GenerateFieldsFromXmlAction extends AnAction implements DumbAware {
    public GenerateFieldsFromXmlAction() {
        getTemplatePresentation().setText("Generate Fields From XML");
        getTemplatePresentation().setDescription("Generate Java entity field drafts from selected MyBatis XML fragment");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        e.getPresentation().setEnabledAndVisible(project != null
                && psiFile instanceof XmlFile
                && findSupportedTag(element) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || !(psiFile instanceof XmlFile xmlFile)) {
            return;
        }

        XmlTag contextTag = findSupportedTag(e.getData(CommonDataKeys.PSI_ELEMENT));
        if (contextTag == null) {
            NotificationUtil.error(project, "Current XML location does not support reverse field generation.");
            return;
        }

        PsiClass targetClass = resolveTargetClass(project, contextTag);
        if (targetClass == null) {
            NotificationUtil.error(project, "Unable to resolve target entity class from current XML.");
            return;
        }

        String fragment = resolveFragmentText(e.getData(CommonDataKeys.EDITOR), contextTag);
        List<XmlFieldSyncSupport.XmlFieldDraft> parsedDrafts = "resultMap".equalsIgnoreCase(contextTag.getName())
                ? XmlFieldSyncSupport.parseResultMap(fragment)
                : XmlFieldSyncSupport.parseBaseColumnList(fragment);

        FieldSyncService service = project.getService(FieldSyncService.class);
        FieldSyncService.ReverseGenerationResult result = service.generateFieldsFromXml(targetClass, parsedDrafts);

        String preview = XmlFieldSyncSupport.buildReversePreview(result.newDrafts(), result.conflicts(),
                targetClass.getQualifiedName() == null ? targetClass.getName() : targetClass.getQualifiedName());
        PreviewDialog previewDialog = new PreviewDialog(project,
                "Preview Generated Fields",
                "Write Fields",
                "Cancel",
                preview);
        if (!previewDialog.showAndGet()) {
            return;
        }

        service.applyGeneratedFields(targetClass, result.newDrafts());
        if (result.newDrafts().isEmpty()) {
            NotificationUtil.info(project, "No new fields were written. Existing fields were kept unchanged.");
        } else if (result.conflicts().isEmpty()) {
            NotificationUtil.info(project, "Generated " + result.newDrafts().size() + " field(s) into "
                    + targetClass.getName() + ".");
        } else {
            NotificationUtil.info(project, "Generated " + result.newDrafts().size() + " field(s). Conflicts skipped: "
                    + String.join(", ", result.conflicts()));
        }
    }

    private XmlTag findSupportedTag(PsiElement element) {
        XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
        while (tag != null) {
            if ("resultMap".equalsIgnoreCase(tag.getName())) {
                return tag;
            }
            if ("sql".equalsIgnoreCase(tag.getName())) {
                String id = tag.getAttributeValue("id");
                if (id != null && id.toLowerCase(Locale.ROOT).contains("column")) {
                    return tag;
                }
            }
            tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class);
        }
        return null;
    }

    private String resolveFragmentText(Editor editor, XmlTag contextTag) {
        if (editor != null && editor.getSelectionModel().hasSelection()) {
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (selectedText != null && !selectedText.isBlank()) {
                return selectedText;
            }
        }
        return "resultMap".equalsIgnoreCase(contextTag.getName()) ? contextTag.getText() : contextTag.getValue().getText();
    }

    private PsiClass resolveTargetClass(Project project, XmlTag contextTag) {
        if ("resultMap".equalsIgnoreCase(contextTag.getName())) {
            String type = firstNonBlank(
                    contextTag.getAttributeValue("type"),
                    contextTag.getAttributeValue("javaType"),
                    contextTag.getAttributeValue("ofType"));
            PsiClass direct = resolveClass(project, type);
            if (direct != null) {
                return direct;
            }
        }

        XmlFile file = contextTag.getContainingFile() instanceof XmlFile xmlFile ? xmlFile : null;
        XmlTag rootTag = file == null ? null : file.getRootTag();
        if (rootTag == null) {
            return null;
        }
        String namespace = rootTag.getAttributeValue("namespace");
        if (namespace == null || namespace.isBlank()) {
            return null;
        }

        PsiClass mapperClass = JavaPsiFacade.getInstance(project).findClass(namespace, GlobalSearchScope.projectScope(project));
        if (mapperClass == null) {
            return null;
        }

        String mapperName = mapperClass.getName();
        if (mapperName == null) {
            return null;
        }
        String entityName = mapperName.endsWith("Mapper") ? mapperName.substring(0, mapperName.length() - 6)
                : mapperName.endsWith("Dao") ? mapperName.substring(0, mapperName.length() - 3)
                : mapperName;
        PsiClass[] candidates = PsiShortNamesCache.getInstance(project).getClassesByName(entityName,
                GlobalSearchScope.projectScope(project));
        if (candidates.length == 0) {
            return null;
        }

        List<PsiClass> ordered = new ArrayList<>(List.of(candidates));
        String mapperPackage = mapperClass.getQualifiedName() == null ? "" : mapperClass.getQualifiedName();
        ordered.sort(Comparator.comparingInt(candidate -> distanceToMapper(mapperPackage, candidate)));
        return ordered.get(0);
    }

    private int distanceToMapper(String mapperQualifiedName, PsiClass candidate) {
        String qn = candidate.getQualifiedName();
        if (qn == null) {
            return Integer.MAX_VALUE;
        }
        int index = 0;
        while (index < mapperQualifiedName.length() && index < qn.length()
                && mapperQualifiedName.charAt(index) == qn.charAt(index)) {
            index++;
        }
        return mapperQualifiedName.length() - index;
    }

    private PsiClass resolveClass(Project project, String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        PsiClass direct = JavaPsiFacade.getInstance(project).findClass(type, GlobalSearchScope.projectScope(project));
        if (direct != null) {
            return direct;
        }
        PsiClass[] candidates = PsiShortNamesCache.getInstance(project).getClassesByName(type,
                GlobalSearchScope.projectScope(project));
        return candidates.length > 0 ? candidates[0] : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
