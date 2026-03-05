package com.eagga.mybatisfieldsync.marker;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class MapperLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element instanceof PsiIdentifier && element.getParent() instanceof PsiMethod psiMethod) {
            if (element != psiMethod.getNameIdentifier())
                return;

            PsiClass psiClass = psiMethod.getContainingClass();
            if (psiClass != null && psiClass.isInterface()) {
                String qualifiedName = psiClass.getQualifiedName();
                String className = psiClass.getName();
                if (qualifiedName == null || className == null)
                    return;

                String methodName = psiMethod.getName();
                Project project = element.getProject();

                // Fast lookup matching common filenames to avoid slow FileTypeIndex scanning
                Collection<XmlFile> fastFiles = new ArrayList<>();
                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
                for (String fileName : new String[] { className + ".xml", className + "Mapper.xml" }) {
                    for (com.intellij.openapi.vfs.VirtualFile vf : com.intellij.psi.search.FilenameIndex
                            .getVirtualFilesByName(fileName, scope)) {
                        PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
                        if (psiFile instanceof XmlFile xf) {
                            fastFiles.add(xf);
                        }
                    }
                }

                Collection<XmlTag> targets = new ArrayList<>();
                for (XmlFile xmlFile : fastFiles) {
                    XmlTag rootTag = xmlFile.getRootTag();
                    if (rootTag != null && "mapper".equals(rootTag.getName())) {
                        for (XmlTag subTag : rootTag.getSubTags()) {
                            if (methodName.equals(subTag.getAttributeValue("id"))) {
                                targets.add(subTag);
                            }
                        }
                    }
                }

                if (!targets.isEmpty()) {
                    NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                            .create(AllIcons.Gutter.ImplementedMethod)
                            .setTargets(targets)
                            .setTooltipText("Navigate to MyBatis XML")
                            .setAlignment(com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT);
                    result.add(builder.createLineMarkerInfo(element));
                }
            }
        }
    }
}
