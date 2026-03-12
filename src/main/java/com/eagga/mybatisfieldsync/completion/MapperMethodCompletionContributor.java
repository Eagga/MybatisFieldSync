package com.eagga.mybatisfieldsync.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.*;
import com.eagga.mybatisfieldsync.util.FieldIgnoreUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class MapperMethodCompletionContributor extends CompletionContributor implements DumbAware {
    private static final List<String> SUPPORTED_OPERATORS = List.of(
            "Equals", "GreaterThan", "LessThan", "GreaterThanEqual", "LessThanEqual",
            "Like", "NotLike", "In", "NotIn", "Between", "IsNull", "IsNotNull");
    
    public MapperMethodCompletionContributor() {
        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(JavaLanguage.INSTANCE),
            new CompletionProvider<>() {
                @Override
                protected void addCompletions(@NotNull CompletionParameters parameters,
                                            @NotNull ProcessingContext context,
                                            @NotNull CompletionResultSet result) {
                    PsiElement position = parameters.getPosition();
                    PsiClass mapperClass = getMapperClass(position);
                    if (mapperClass == null || !mapperClass.isInterface()) return;
                    
                    PsiClass entityClass = findEntityClass(mapperClass);
                    if (entityClass == null) return;

                    List<PsiField> fields = Arrays.stream(entityClass.getAllFields())
                            .filter(f -> !FieldIgnoreUtil.shouldIgnore(f))
                            .toList();
                    String prefix = resolvePrefix(parameters, result);
                    
                    if (prefix.startsWith("findBy") || prefix.startsWith("countBy") || 
                        prefix.startsWith("deleteBy") || prefix.startsWith("existsBy")) {
                        generateCompletions(result, fields, entityClass, prefix);
                    }
                }
            });
    }
    
    private PsiClass getMapperClass(PsiElement element) {
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method != null) return method.getContainingClass();
        return PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }

    private String resolvePrefix(CompletionParameters parameters, CompletionResultSet result) {
        String prefix = result.getPrefixMatcher().getPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix;
        }

        PsiElement position = parameters.getPosition();
        PsiElement parent = position.getParent();
        if (parent instanceof PsiIdentifier identifier) {
            return identifier.getText();
        }
        if (position instanceof PsiIdentifier identifier) {
            return identifier.getText();
        }
        return "";
    }
    
    private PsiClass findEntityClass(PsiClass mapperClass) {
        PsiClass fromGeneric = findEntityFromSuperTypes(mapperClass);
        if (fromGeneric != null) {
            return fromGeneric;
        }

        String mapperName = mapperClass.getName();
        if (mapperName != null && mapperName.endsWith("Mapper")) {
            String entityName = mapperName.substring(0, mapperName.length() - 6);
            return findClassByShortName(mapperClass, entityName);
        }
        return null;
    }

    private PsiClass findEntityFromSuperTypes(PsiClass mapperClass) {
        for (PsiClassType type : mapperClass.getSuperTypes()) {
            PsiType[] parameters = type.getParameters();
            if (parameters.length == 0) {
                continue;
            }
            for (PsiType parameter : parameters) {
                if (parameter instanceof PsiClassType classType) {
                    PsiClass resolved = classType.resolve();
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
        }
        return null;
    }

    private PsiClass findClassByShortName(PsiClass mapperClass, String shortName) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(mapperClass.getProject());
        PsiClass[] candidates = PsiShortNamesCache.getInstance(mapperClass.getProject())
                .getClassesByName(shortName, scope);
        if (candidates.length == 0) {
            return null;
        }
        if (candidates.length == 1) {
            return candidates[0];
        }
        String mapperPackage = packageNameOf(mapperClass);
        for (PsiClass candidate : candidates) {
            String candidatePackage = packageNameOf(candidate);
            if (!mapperPackage.isBlank() && candidatePackage.startsWith(mapperPackage)) {
                return candidate;
            }
        }
        return candidates[0];
    }

    private String packageNameOf(PsiClass psiClass) {
        PsiFile file = psiClass.getContainingFile();
        if (!(file instanceof PsiJavaFile javaFile)) {
            return "";
        }
        return javaFile.getPackageName();
    }
    
    private void generateCompletions(CompletionResultSet result, List<PsiField> fields, 
                                    PsiClass entityClass, String prefix) {
        String methodPrefix = extractPrefix(prefix);
        
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            if (FieldIgnoreUtil.shouldIgnore(field)) continue;
            
            String fieldName = field.getName();
            String capitalizedField = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            
            // 单字段方法
            String methodName = methodPrefix + capitalizedField;
            addCompletion(result, methodName, entityClass);
            
            // 带操作符的方法
            for (String op : SUPPORTED_OPERATORS) {
                if ("Equals".equals(op)) {
                    continue;
                }
                addCompletion(result, methodPrefix + capitalizedField + op, entityClass);
            }
        }
        
        // 多字段组合（最多2个字段）
        for (int i = 0; i < Math.min(fields.size(), 5); i++) {
            for (int j = i + 1; j < Math.min(fields.size(), 5); j++) {
                String field1 = capitalize(fields.get(i).getName());
                String field2 = capitalize(fields.get(j).getName());
                addCompletion(result, methodPrefix + field1 + "And" + field2, entityClass);
            }
        }
    }
    
    private String extractPrefix(String text) {
        for (String p : List.of("findBy", "countBy", "deleteBy", "existsBy")) {
            if (text.startsWith(p)) return p;
        }
        return "findBy";
    }
    
    private String capitalize(String str) {
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    private void addCompletion(CompletionResultSet result, String methodName, PsiClass entityClass) {
        MethodNameParser.ParseResult parseResult = MethodNameParser.parse(methodName);
        result.addElement(LookupElementBuilder.create(methodName)
            .withInsertHandler(new MapperMethodInsertHandler(entityClass, parseResult)));
    }
}
