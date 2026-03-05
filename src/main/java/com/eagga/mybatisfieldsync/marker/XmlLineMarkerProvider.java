package com.eagga.mybatisfieldsync.marker;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class XmlLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element instanceof XmlAttributeValue) {
            XmlAttribute attr = (XmlAttribute) element.getParent();
            if (attr != null && "id".equals(attr.getName())) {
                XmlTag tag = attr.getParent();
                if (tag != null) {
                    String tagName = tag.getName();
                    if ("select".equals(tagName) || "insert".equals(tagName) || "update".equals(tagName)
                            || "delete".equals(tagName) || "sql".equals(tagName)) {
                        XmlTag parentTag = tag.getParentTag();
                        if (parentTag != null && "mapper".equals(parentTag.getName())) {
                            String namespace = parentTag.getAttributeValue("namespace");
                            String id = tag.getAttributeValue("id");
                            if (namespace != null && id != null) {
                                Project project = element.getProject();
                                PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(namespace,
                                        GlobalSearchScope.projectScope(project));
                                if (psiClass != null) {
                                    PsiMethod[] methods = psiClass.findMethodsByName(id, false);
                                    if (methods.length > 0) {
                                        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                                                .create(AllIcons.Gutter.ImplementingMethod)
                                                .setTargets(methods)
                                                .setTooltipText("Navigate to Mapper Java Interface")
                                                .setAlignment(
                                                        com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT);
                                        result.add(builder.createLineMarkerInfo(element));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
