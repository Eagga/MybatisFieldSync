package com.eagga.mybatisfieldsync.marker;

import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.*;
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
                if (psiClass.getName() == null) {
                    return;
                }

                String methodName = psiMethod.getName();
                FieldSyncService fieldSyncService = element.getProject().getService(FieldSyncService.class);
                Collection<XmlFile> candidateXmlFiles = new ArrayList<>(fieldSyncService.findCandidateXmlFiles(psiClass));

                Collection<XmlTag> targets = new ArrayList<>();
                for (XmlFile xmlFile : candidateXmlFiles) {
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
