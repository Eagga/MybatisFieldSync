package com.eagga.mybatisfieldsync.service;

import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Mapper 接口方法生成服务
 */
@Service(Service.Level.PROJECT)
public final class MapperInterfaceService {
    private final Project project;

    public MapperInterfaceService(Project project) {
        this.project = project;
    }

    /**
     * 为 Mapper 接口生成方法
     */
    public void generateMapperMethods(@NotNull PsiClass mapperInterface,
                                      @NotNull PsiClass entityClass,
                                      @NotNull List<FieldInfo> fields,
                                      @NotNull Set<String> methodTypes) {
        WriteCommandAction.runWriteCommandAction(project, "Generate Mapper Methods", null, () -> {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

            if (methodTypes.contains("insert")) {
                addMethodIfNotExists(mapperInterface, factory, generateInsertMethod(factory, entityClass));
            }
            if (methodTypes.contains("update")) {
                addMethodIfNotExists(mapperInterface, factory, generateUpdateMethod(factory, entityClass));
            }
            if (methodTypes.contains("delete")) {
                addMethodIfNotExists(mapperInterface, factory, generateDeleteMethod(factory, entityClass));
            }
            if (methodTypes.contains("selectById")) {
                addMethodIfNotExists(mapperInterface, factory, generateSelectByIdMethod(factory, entityClass));
            }
            if (methodTypes.contains("selectList")) {
                addMethodIfNotExists(mapperInterface, factory, generateSelectListMethod(factory, entityClass));
            }

            // 格式化代码
            CodeStyleManager.getInstance(project).reformat(mapperInterface);
        });
    }

    private void addMethodIfNotExists(@NotNull PsiClass mapperInterface,
                                      @NotNull PsiElementFactory factory,
                                      @NotNull PsiMethod method) {
        // 检查方法是否已存在
        PsiMethod[] existingMethods = mapperInterface.findMethodsByName(method.getName(), false);
        if (existingMethods.length == 0) {
            mapperInterface.add(method);
        }
    }

    private @NotNull PsiMethod generateInsertMethod(@NotNull PsiElementFactory factory,
                                                     @NotNull PsiClass entityClass) {
        String entityName = entityClass.getName();
        String methodText = "int insert(" + entityName + " record);";
        return factory.createMethodFromText(methodText, null);
    }

    private @NotNull PsiMethod generateUpdateMethod(@NotNull PsiElementFactory factory,
                                                     @NotNull PsiClass entityClass) {
        String entityName = entityClass.getName();
        String methodText = "int update(" + entityName + " record);";
        return factory.createMethodFromText(methodText, null);
    }

    private @NotNull PsiMethod generateDeleteMethod(@NotNull PsiElementFactory factory,
                                                     @NotNull PsiClass entityClass) {
        String methodText = "int delete(Long id);";
        return factory.createMethodFromText(methodText, null);
    }

    private @NotNull PsiMethod generateSelectByIdMethod(@NotNull PsiElementFactory factory,
                                                         @NotNull PsiClass entityClass) {
        String entityName = entityClass.getName();
        String methodText = entityName + " selectById(Long id);";
        return factory.createMethodFromText(methodText, null);
    }

    private @NotNull PsiMethod generateSelectListMethod(@NotNull PsiElementFactory factory,
                                                         @NotNull PsiClass entityClass) {
        String entityName = entityClass.getName();
        String methodText = "java.util.List<" + entityName + "> selectList();";
        return factory.createMethodFromText(methodText, null);
    }

    /**
     * 查找实体类对应的 Mapper 接口
     */
    public @Nullable PsiClass findMapperInterface(@NotNull PsiClass entityClass) {
        String entityName = entityClass.getName();
        if (entityName == null) {
            return null;
        }

        // 尝试查找 EntityMapper 或 EntityDao
        String[] possibleNames = {
            entityName + "Mapper",
            entityName + "Dao"
        };

        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        String packageName = ((PsiJavaFile) entityClass.getContainingFile()).getPackageName();

        // 尝试在同包或 mapper/dao 子包中查找
        String[] possiblePackages = {
            packageName + ".mapper",
            packageName + ".dao",
            packageName
        };

        for (String pkg : possiblePackages) {
            for (String name : possibleNames) {
                String fqn = pkg + "." + name;
                PsiClass mapperClass = javaPsiFacade.findClass(fqn, GlobalSearchScope.projectScope(project));
                if (mapperClass != null && mapperClass.isInterface()) {
                    return mapperClass;
                }
            }
        }

        return null;
    }
}
