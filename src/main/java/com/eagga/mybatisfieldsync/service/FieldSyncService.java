package com.eagga.mybatisfieldsync.service;

import com.eagga.mybatisfieldsync.database.DatabaseFieldEnhancer;
import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.model.StatementInfo;
import com.eagga.mybatisfieldsync.model.SyncException;
import com.eagga.mybatisfieldsync.util.FieldIgnoreUtil;
import com.eagga.mybatisfieldsync.settings.MyBatisFieldSyncSettings;
import com.eagga.mybatisfieldsync.util.IndentUtil;
import com.eagga.mybatisfieldsync.util.JdbcTypeUtil;
import com.eagga.mybatisfieldsync.util.NameUtil;
import com.eagga.mybatisfieldsync.util.TypeMappingUtil;
import com.eagga.mybatisfieldsync.util.XmlFormatSettingsUtil;
import com.eagga.mybatisfieldsync.util.XmlFieldSyncSupport;
import com.eagga.mybatisfieldsync.util.XmlMappingRenderUtil;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
/**
 * 核心领域服务：
 * 1）收集实体字段；2）定位目标 Mapper XML；3）执行 Statement 同步。
 */
public final class FieldSyncService {
    private static final Pattern INSERT_VALUES_PATTERN = Pattern
            .compile("(?is)\\(\\s*[^()]*\\)\\s*values\\s*\\(\\s*[^()]*\\)");
    private static final Set<String> STATEMENT_TAGS = new HashSet<>(
            List.of("insert", "update", "delete", "select", "sql", "resultMap"));
    private final Project project;

    public FieldSyncService(Project project) {
        this.project = project;
    }

    /**
     * 收集当前类及可选父类的字段。
     * 会忽略 static 字段，并按首次出现的字段名去重。
     */
    public @NotNull List<FieldInfo> collectFields(@NotNull PsiClass psiClass, boolean includeInherited) {
        LinkedHashMap<String, FieldInfo> result = new LinkedHashMap<>();

        PsiClass cursor = psiClass;
        while (cursor != null) {
            boolean inherited = !Objects.equals(cursor, psiClass);
            for (PsiField field : cursor.getFields()) {
                if (field.hasModifierProperty("static")) {
                    continue;
                }
                if (result.containsKey(field.getName())) {
                    continue;
                }
                if (FieldIgnoreUtil.shouldIgnore(field)) {
                    continue;
                }

                result.put(field.getName(), new FieldInfo(
                        field,
                        field.getName(),
                        field.getType().getPresentableText(),
                        JdbcTypeUtil.resolveJdbcType(project, field.getType().getCanonicalText()),
                        cursor.getName() == null ? "" : cursor.getName(),
                        inherited));
            }

            if (!includeInherited) {
                break;
            }
            cursor = cursor.getSuperClass();
            if (cursor != null && "java.lang.Object".equals(cursor.getQualifiedName())) {
                break;
            }
        }

        List<FieldInfo> fields = new ArrayList<>(result.values());

        // 尝试使用数据库信息增强字段类型映射
        try {
            DatabaseFieldEnhancer enhancer = new DatabaseFieldEnhancer(project);
            fields = enhancer.enhanceFields(psiClass, fields);
        } catch (Throwable ignored) {
            // 数据库插件不可用时忽略
        }

        return fields;
    }

    /**
     * 通过文件名约定和 mapper namespace 匹配查找候选 XML。
     */
    public @NotNull List<XmlFile> findCandidateXmlFiles(@NotNull PsiClass psiClass) {
        String className = psiClass.getName();
        if (className == null || className.isBlank()) {
            return List.of();
        }

        String qualifiedName = psiClass.getQualifiedName();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Set<XmlFile> files = new LinkedHashSet<>();

        addByFileName(files, className + ".xml", scope);
        addByFileName(files, className + "Mapper.xml", scope);

        // namespace 扫描用于覆盖文件名不符合命名约定的场景。
        FileTypeIndex.processFiles(XmlFileType.INSTANCE, virtualFile -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (!(psiFile instanceof XmlFile xmlFile)) {
                return true;
            }

            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null || !"mapper".equals(rootTag.getName())) {
                return true;
            }

            String namespace = rootTag.getAttributeValue("namespace");
            if (namespace == null || namespace.isBlank()) {
                return true;
            }

            if (namespaceMatches(className, qualifiedName, namespace)) {
                files.add(xmlFile);
            }

            return true;
        }, scope);

        Module sourceModule = ModuleUtilCore.findModuleForPsiElement(psiClass);
        Set<String> dependencyModuleNames = sourceModule == null ? Set.of() : collectDependencyModuleNames(sourceModule);
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

        return sortByModulePriority(new ArrayList<>(files),
                xmlFile -> resolveModuleName(fileIndex, xmlFile),
                this::resolveXmlPath,
                sourceModule == null ? null : sourceModule.getName(),
                dependencyModuleNames);
    }

    static <T> @NotNull List<T> sortByModulePriority(@NotNull List<T> candidates,
            @NotNull Function<T, String> moduleNameExtractor,
            @NotNull Function<T, String> pathExtractor,
            String sourceModuleName,
            @NotNull Set<String> dependencyModuleNames) {
        List<T> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.<T>comparingInt(candidate -> resolveModulePriority(moduleNameExtractor.apply(candidate),
                        sourceModuleName,
                        dependencyModuleNames))
                .thenComparing(candidate -> normalizeSortValue(pathExtractor.apply(candidate)),
                        String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * 收集 XML 中可供选择、且带 id 的 SQL Statement。
     */
    public @NotNull List<StatementInfo> collectStatements(@NotNull XmlFile xmlFile) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return List.of();
        }

        List<StatementInfo> result = new ArrayList<>();
        collectStatementsRecursively(rootTag, result);
        return result;
    }

    public @NotNull ReverseGenerationResult generateFieldsFromXml(@NotNull PsiClass targetClass,
            @NotNull List<XmlFieldSyncSupport.XmlFieldDraft> drafts) {
        List<FieldInfo> existingFields = collectFields(targetClass, true);
        Set<String> existingNames = new HashSet<>();
        for (FieldInfo field : existingFields) {
            existingNames.add(field.name());
        }

        List<XmlFieldSyncSupport.XmlFieldDraft> newDrafts = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (XmlFieldSyncSupport.XmlFieldDraft draft : drafts) {
            if (existingNames.contains(draft.fieldName())) {
                conflicts.add(draft.fieldName());
                continue;
            }
            newDrafts.add(draft);
            existingNames.add(draft.fieldName());
        }
        return new ReverseGenerationResult(newDrafts, conflicts);
    }

    public void applyGeneratedFields(@NotNull PsiClass targetClass,
            @NotNull List<XmlFieldSyncSupport.XmlFieldDraft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, "Generate Fields From MyBatis XML", null, () -> {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiJavaFile javaFile = targetClass.getContainingFile() instanceof PsiJavaFile file ? file : null;
            PsiElement anchor = targetClass.getRBrace();
            if (anchor == null) {
                anchor = targetClass.getLastChild();
            }
            for (XmlFieldSyncSupport.XmlFieldDraft draft : drafts) {
                String fieldText = XmlFieldSyncSupport.toJavaFieldSnippet(draft);
                PsiField field = factory.createFieldFromText(fieldText, targetClass);
                targetClass.addBefore(field, anchor);
                targetClass.addBefore(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n"), anchor);
            }
            CodeStyleManager.getInstance(project).reformat(targetClass);
            if (javaFile != null) {
                JavaCodeStyleManager.getInstance(project).optimizeImports(javaFile);
            }
        }, targetClass.getContainingFile());
    }

    public void syncCommentsInWriteCommand(@NotNull XmlFile xmlFile,
            @NotNull StatementInfo statementInfo,
            @NotNull List<FieldInfo> selectedFields) {
        WriteCommandAction.runWriteCommandAction(project,
                "Sync Field Comments to MyBatis XML",
                null,
                () -> syncFieldComments(statementInfo.tag(), selectedFields),
                xmlFile);
    }

    public void renameFieldReferences(@NotNull PsiClass entityClass, @NotNull String oldName, @NotNull String newName) {
        if (Objects.equals(oldName, newName)) {
            return;
        }
        List<XmlFile> xmlFiles = findCandidateXmlFiles(entityClass);
        if (xmlFiles.isEmpty()) {
            return;
        }

        Runnable renameTask = () -> {
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            for (XmlFile xmlFile : xmlFiles) {
                Document document = documentManager.getDocument(xmlFile);
                if (document == null) {
                    continue;
                }
                String original = document.getText();
                String updated = renameFieldReferencesInText(original, oldName, newName);
                if (!updated.equals(original)) {
                    document.setText(updated);
                    documentManager.commitDocument(document);
                }
            }
        };

        if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
            renameTask.run();
            return;
        }

        WriteCommandAction.runWriteCommandAction(project,
                "Rename MyBatis Field References",
                null,
                renameTask,
                xmlFiles.toArray(PsiFile[]::new));
    }

    private @NotNull String renameFieldReferencesInText(@NotNull String xmlText, @NotNull String oldName,
            @NotNull String newName) {
        String updated = xmlText.replaceAll("(property\\s*=\\s*)([\"'])" + Pattern.quote(oldName) + "\\2",
                "$1$2" + Matcher.quoteReplacement(newName) + "$2");

        for (String attributeName : List.of("test", "collection", "item", "index")) {
            updated = renameXmlAttributeExpression(updated, attributeName, oldName, newName);
        }

        updated = XmlFieldSyncSupport.renamePlaceholders(updated, oldName, newName);
        updated = XmlFieldSyncSupport.renameDollarPlaceholders(updated, oldName, newName);
        return updated;
    }

    private @NotNull String renameXmlAttributeExpression(@NotNull String xmlText,
            @NotNull String attributeName,
            @NotNull String oldName,
            @NotNull String newName) {
        Matcher matcher = Pattern.compile(attributeName + "\\s*=\\s*([\"'])(.*?)\\1").matcher(xmlText);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String renamed = XmlFieldSyncSupport.renameTestExpression(matcher.group(2), oldName, newName);
            matcher.appendReplacement(buffer,
                    Matcher.quoteReplacement(attributeName + "=" + matcher.group(1) + renamed + matcher.group(1)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 在写命令中执行单个 Statement 同步，确保支持 IDE 的撤销/重做。
     */
    public void syncInWriteCommand(@NotNull XmlFile xmlFile,
            @NotNull StatementInfo statementInfo,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder,
            @NotNull String entityClassName) throws SyncException {
        syncInWriteCommand(xmlFile, statementInfo, selectedFields, allFieldsInOrder, entityClassName, true);
    }

    public void syncInWriteCommand(@NotNull XmlFile xmlFile,
            @NotNull StatementInfo statementInfo,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder,
            @NotNull String entityClassName,
            boolean recordHistory) throws SyncException {
        AtomicReference<SyncException> exceptionRef = new AtomicReference<>();
        WriteCommandAction.runWriteCommandAction(project,
                MyBatisFieldSyncBundle.message("action.syncFields.text"),
                null,
                () -> {
                    try {
                        syncStatement(statementInfo, selectedFields, allFieldsInOrder);
                    } catch (SyncException e) {
                        exceptionRef.set(e);
                    }
                },
                xmlFile);

        if (exceptionRef.get() != null) {
            throw exceptionRef.get();
        }

        if (recordHistory) {
            SyncHistoryService historyService = project.getService(SyncHistoryService.class);
            historyService.addEntry(entityClassName, xmlFile.getName(), statementInfo.id(),
                    selectedFields.stream().map(FieldInfo::name).toList());
        }
    }

    private void syncStatement(@NotNull StatementInfo statementInfo,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
        if (selectedFields.isEmpty()) {
            throw new SyncException(MyBatisFieldSyncBundle.message("notify.noField"));
        }

        XmlTag statementTag = statementInfo.tag();
        String tagName = statementTag.getName().toLowerCase(Locale.ROOT);
        String id = statementInfo.id();

        if ("insert".equals(tagName) || id.toLowerCase(Locale.ROOT).contains("insert")) {
            if (id.toLowerCase(Locale.ROOT).contains("batch")
                    || !findNestedTagsByName(statementTag, "foreach").isEmpty()) {
                syncBatchInsert(statementTag, selectedFields, allFieldsInOrder);
            } else {
                syncInsert(statementTag, selectedFields, allFieldsInOrder);
            }
            return;
        }

        if ("update".equals(tagName) || id.toLowerCase(Locale.ROOT).contains("update")) {
            syncUpdate(statementTag, selectedFields, allFieldsInOrder);
            return;
        }

        if ("sql".equals(tagName) && id.toLowerCase(Locale.ROOT).contains("column")) {
            syncBaseColumnList(statementTag, selectedFields, allFieldsInOrder);
            return;
        }

        if (id.toLowerCase(Locale.ROOT).contains("where") || !findNestedTagsByName(statementTag, "where").isEmpty()) {
            syncWhere(statementTag, selectedFields, allFieldsInOrder);
            return;
        }

        if ("resultMap".equals(tagName) || id.toLowerCase(Locale.ROOT).contains("result")) {
            syncResultMap(statementTag, selectedFields, allFieldsInOrder);
            return;
        }

        throw new SyncException(MyBatisFieldSyncBundle.message("notify.unsupported", id));
    }

    private void syncFieldComments(@NotNull XmlTag statementTag, @NotNull List<FieldInfo> selectedFields) {
        String tagName = statementTag.getName().toLowerCase(Locale.ROOT);
        String id = Objects.requireNonNullElse(statementTag.getAttributeValue("id"), "");
        if ("resultmap".equals(tagName) || id.toLowerCase(Locale.ROOT).contains("result")) {
            String body = statementTag.getValue().getText();
            for (FieldInfo field : selectedFields) {
                String comment = resolveFieldComment(field);
                if (comment.isBlank()) {
                    continue;
                }
                body = XmlFieldSyncSupport.upsertCommentBeforeLine(body, resultPatternFor(field), comment);
            }
            statementTag.getValue().setText(body);
            return;
        }

        if ("sql".equals(tagName) && id.toLowerCase(Locale.ROOT).contains("column")) {
            String body = statementTag.getValue().getText();
            for (FieldInfo field : selectedFields) {
                String comment = resolveFieldComment(field);
                if (comment.isBlank()) {
                    continue;
                }
                body = XmlFieldSyncSupport.upsertCommentBeforeLine(body, columnPatternFor(field), comment);
            }
            statementTag.getValue().setText(body);
            return;
        }

        syncCommentsRecursively(statementTag, selectedFields);
    }

    private void syncCommentsRecursively(@NotNull XmlTag tag, @NotNull List<FieldInfo> selectedFields) {
        if (tag.getSubTags().length == 0) {
            String body = tag.getValue().getText();
            String updated = body;
            for (FieldInfo field : selectedFields) {
                String comment = resolveFieldComment(field);
                if (comment.isBlank()) {
                    continue;
                }
                updated = XmlFieldSyncSupport.upsertCommentBeforeLine(updated, assignmentPatternFor(field), comment);
                updated = XmlFieldSyncSupport.upsertCommentBeforeLine(updated, columnPatternFor(field), comment);
                updated = XmlFieldSyncSupport.upsertCommentBeforeLine(updated,
                        Pattern.compile("(?i)(^|\\s)and\\s+`?" + Pattern.quote(NameUtil.camelToSnake(field.name()))
                                + "`?\\b"),
                        comment);
            }
            if (!updated.equals(body)) {
                tag.getValue().setText(updated);
            }
            return;
        }

        for (XmlTag subTag : tag.getSubTags()) {
            syncCommentsRecursively(subTag, selectedFields);
        }
    }

    private @NotNull String resolveFieldComment(@NotNull FieldInfo field) {
        String docText = field.psiField().getDocComment() == null ? "" : field.psiField().getDocComment().getText();
        String extracted = XmlFieldSyncSupport.extractFieldComment(docText);
        if (!extracted.isBlank()) {
            return extracted;
        }
        PsiElement sibling = field.psiField().getPrevSibling();
        while (sibling instanceof PsiWhiteSpace) {
            sibling = sibling.getPrevSibling();
        }
        if (sibling instanceof PsiComment psiComment) {
            return XmlFieldSyncSupport.extractFieldComment(psiComment.getText());
        }
        return "";
    }

    /**
     * 同步 insert 语句：
     * - 优先：对 trim（列/值）增量补齐，保持列和值位置对应
     * - 回退：替换普通 SQL 文本中的 "(...) VALUES (...)" 片段
     */
    private void syncInsert(@NotNull XmlTag insertTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
        List<DynamicSqlStructureSupport.InsertTrimPlan> trimPlans =
                DynamicSqlStructureSupport.findInsertTrimPlans(new PsiXmlTagView(insertTag), false);
        if (!trimPlans.isEmpty()) {
            for (DynamicSqlStructureSupport.InsertTrimPlan trimPlan : trimPlans) {
                XmlTag columnTrim = resolveTagByElementPath(insertTag, trimPlan.columnPath());
                XmlTag valueTrim = resolveTagByElementPath(insertTag, trimPlan.valuePath());
                if (columnTrim != null && valueTrim != null) {
                    mergeInsertTrim(columnTrim, valueTrim, selectedFields, allFieldsInOrder);
                }
            }
            return;
        }

        List<String> columns = selectedFields.stream().map(field -> NameUtil.camelToSnake(field.name())).toList();
        List<String> values = selectedFields.stream().map(this::buildParamPlaceholder).toList();

        String statementBody = insertTag.getValue().getText();
        if (statementBody.contains("<if") || statementBody.contains("</if>")) {
            if (!mergeInsertWithoutTrimIf(insertTag, selectedFields, allFieldsInOrder)) {
                throw new SyncException(MyBatisFieldSyncBundle.message("notify.insertComplexUnsupported"));
            }
            return;
        }
        Matcher matcher = INSERT_VALUES_PATTERN.matcher(statementBody);
        if (!matcher.find()) {
            throw new SyncException(MyBatisFieldSyncBundle.message("notify.insertBlockMissing"));
        }

        String replacement = "(" + String.join(", ", columns) + ") VALUES (" + String.join(", ", values) + ")";
        String updated = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        insertTag.getValue().setText(updated);
    }

    /**
     * 同步 update 语句：
     * - 优先：对 set 标签做增量补齐（若已使用 if，则新增项也使用 if）
     * - 回退：改写 "SET ... WHERE" 文本区间
     */

    private void syncBatchInsert(@NotNull XmlTag insertTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
        List<XmlTag> foreachTags = findNestedTagsByName(insertTag, "foreach");
        if (foreachTags.isEmpty()) {
            throw new SyncException("Batch insert statement must contain <foreach> tag");
        }
        List<DynamicSqlStructureSupport.InsertTrimPlan> trimPlans =
                DynamicSqlStructureSupport.findInsertTrimPlans(new PsiXmlTagView(insertTag), true);
        if (!trimPlans.isEmpty()) {
            for (DynamicSqlStructureSupport.InsertTrimPlan trimPlan : trimPlans) {
                XmlTag columnTrim = resolveTagByElementPath(insertTag, trimPlan.columnPath());
                XmlTag valueTrim = resolveTagByElementPath(insertTag, trimPlan.valuePath());
                if (columnTrim == null || valueTrim == null) {
                    continue;
                }
                String item = trimPlan.foreachItem() == null || trimPlan.foreachItem().isBlank()
                        ? "item"
                        : trimPlan.foreachItem();
                mergeBatchInsertTrim(columnTrim, valueTrim, item, selectedFields, allFieldsInOrder);
            }
            return;
        }

        XmlTag foreachTag = foreachTags.get(0);
        String itemRaw = foreachTag.getAttributeValue("item");
        final String item = (itemRaw == null || itemRaw.isBlank()) ? "item" : itemRaw;

        List<XmlTag> trimTags = findNestedTagsByName(insertTag, "trim");
        if (trimTags.size() >= 2) {
            mergeBatchInsertTrim(trimTags.get(0), trimTags.get(1), item, selectedFields, allFieldsInOrder);
            return;
        }

        if (trimTags.size() == 1) {
            List<XmlTag> foreachTrims = findNestedTagsByName(foreachTag, "trim");
            if (!foreachTrims.isEmpty()) {
                mergeBatchInsertTrim(trimTags.get(0), foreachTrims.get(0), item, selectedFields, allFieldsInOrder);
                return;
            }
        }

        List<String> columns = selectedFields.stream().map(field -> NameUtil.camelToSnake(field.name())).toList();
        List<String> values = selectedFields.stream().map(field -> buildBatchParamPlaceholder(field, item)).toList();

        // simple regex replacement
        String insertText = insertTag.getValue().getText();
        String cols = "(" + String.join(", ", columns) + ")";
        String rawColRe = "(?is)(\\(\\s*[^()]*\\))(\\s*values)";
        Matcher cm = Pattern.compile(rawColRe).matcher(insertText);

        if (cm.find()) {
            insertText = cm.replaceFirst(Matcher.quoteReplacement(cols + cm.group(2)));
        } else {
            // just replace first (...)
            Matcher cm2 = Pattern.compile("(?is)\\(\\s*[^()]*\\)").matcher(insertText);
            if (cm2.find()) {
                insertText = cm2.replaceFirst(Matcher.quoteReplacement(cols));
            } else {
                throw new SyncException("Cannot find (...) block for columns in batch insert");
            }
        }

        insertTag.getValue().setText(insertText);

        // foreach part should have been preserved, now replace its values
        foreachTags = findNestedTagsByName(insertTag, "foreach");
        if (!foreachTags.isEmpty()) {
            XmlTag newForeach = foreachTags.get(0);
            String foreachText = newForeach.getValue().getText();
            String vals = "(" + String.join(", ", values) + ")";
            Matcher vm = Pattern.compile("(?is)\\(\\s*[^()]*\\)").matcher(foreachText);
            if (vm.find()) {
                newForeach.getValue().setText(vm.replaceFirst(Matcher.quoteReplacement(vals)));
            } else {
                newForeach.getValue().setText(vals); // or just set it
            }
        }
    }

    private void mergeBatchInsertTrim(@NotNull XmlTag columnTrim,
            @NotNull XmlTag valueTrim,
            @NotNull String item,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        String columnBody = columnTrim.getValue().getText();
        String valueBody = valueTrim.getValue().getText();
        String columnIndent = detectEntryIndent(columnBody);
        String valueIndent = detectEntryIndent(valueBody);

        boolean changed = false;
        for (FieldInfo field : selectedFields) {
            if (!containsColumnEntry(columnBody, field)) {
                columnBody = insertPlainEntryByFieldOrder(
                        columnBody,
                        NameUtil.camelToSnake(field.name()) + ",",
                        columnIndent,
                        allFieldsInOrder,
                        field,
                        this::columnPatternFor);
                changed = true;
            }
            if (!containsBatchValueEntry(valueBody, field, item)) {
                valueBody = insertPlainEntryByFieldOrder(
                        valueBody,
                        buildBatchParamPlaceholder(field, item) + ",",
                        valueIndent,
                        allFieldsInOrder,
                        field,
                        f -> batchValuePatternFor(f, item));
                changed = true;
            }
        }

        if (changed) {
            columnTrim.getValue().setText(columnBody);
            valueTrim.getValue().setText(valueBody);
        }
    }

    private String buildBatchParamPlaceholder(@NotNull FieldInfo fieldInfo, @NotNull String item) {
        return XmlMappingRenderUtil.buildParameterPlaceholder(item + "." + fieldInfo.name(), resolveTypeMapping(fieldInfo));
    }

    private boolean containsBatchValueEntry(@NotNull String body, @NotNull FieldInfo field, @NotNull String item) {
        return batchValuePatternFor(field, item).matcher(body).find();
    }

    private Pattern batchValuePatternFor(@NotNull FieldInfo field, @NotNull String item) {
        return Pattern.compile("#\\{\\s*" + Pattern.quote(item + "." + field.name()) + "\\b");
    }

    private void syncUpdate(@NotNull XmlTag updateTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
        List<XmlTag> setTags = findNestedTagsByName(updateTag, "set");
        if (!setTags.isEmpty()) {
            for (XmlTag setTag : setTags) {
                syncAssignmentContainers(setTag, selectedFields, allFieldsInOrder);
            }
            return;
        }

        List<XmlTag> chooseTags = findNestedTagsByName(updateTag, "choose");
        if (!chooseTags.isEmpty()) {
            for (XmlTag chooseTag : chooseTags) {
                syncAssignmentContainers(chooseTag, selectedFields, allFieldsInOrder);
            }
            return;
        }

        List<String> assignments = selectedFields.stream()
                .map(field -> NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field))
                .toList();

        String body = updateTag.getValue().getText();
        Pattern pattern = Pattern.compile("(?is)(\\bset\\b)(.*?)(\\bwhere\\b|$)");
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new SyncException(MyBatisFieldSyncBundle.message("notify.updateBlockMissing"));
        }

        String indent = IndentUtil.detectIndentUnit(body);
        String replacement = matcher.group(1) + "\n" + indent + String.join(",\n" + indent, assignments) + "\n"
                + matcher.group(3);
        String updated = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        updateTag.getValue().setText(updated);
    }

    private void mergeChooseTag(@NotNull XmlTag chooseTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        List<XmlTag> whenTags = findNestedTagsByName(chooseTag, "when");
        if (whenTags.isEmpty()) {
            return;
        }

        String indent = detectChildIndent(chooseTag);
        String childIndent = indent + IndentUtil.detectIndentUnit(chooseTag.getText());

        for (FieldInfo field : selectedFields) {
            if (containsUpdateAssignment(chooseTag.getText(), field)) {
                continue;
            }

            String assignment = NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field) + ",";
            XmlTag whenTag = createWhenTag(chooseTag, buildIfTest(field), assignment, childIndent);
            insertWhenTagByFieldOrder(chooseTag, whenTag, allFieldsInOrder, field, this::containsUpdateAssignment);
        }
    }

    private XmlTag createWhenTag(@NotNull XmlTag parent,
            @NotNull String testExpr,
            @NotNull String bodyLine,
            @NotNull String bodyIndent) {
        XmlTag whenTag = parent.createChildTag("when", null, "\n" + bodyIndent + bodyLine + "\n", false);
        whenTag.setAttribute("test", testExpr);
        return whenTag;
    }

    private void insertWhenTagByFieldOrder(@NotNull XmlTag parent,
            @NotNull XmlTag newWhenTag,
            @NotNull List<FieldInfo> orderedFields,
            @NotNull FieldInfo currentField,
            @NotNull BiPredicate<String, FieldInfo> matcher) {
        int currentIndex = orderedFields.indexOf(currentField);
        XmlTag anchor = findNextWhenAnchor(parent, orderedFields, currentIndex, matcher);
        if (anchor == null) {
            parent.addSubTag(newWhenTag, false);
            return;
        }
        parent.addBefore(newWhenTag, anchor);
    }

    private XmlTag findNextWhenAnchor(@NotNull XmlTag parent,
            @NotNull List<FieldInfo> orderedFields,
            int currentIndex,
            @NotNull BiPredicate<String, FieldInfo> matcher) {
        XmlTag[] subTags = parent.getSubTags();
        for (int i = currentIndex + 1; i < orderedFields.size(); i++) {
            FieldInfo candidateField = orderedFields.get(i);
            for (XmlTag subTag : subTags) {
                if (!"when".equalsIgnoreCase(subTag.getName())) {
                    continue;
                }
                if (matcher.test(subTag.getText(), candidateField)) {
                    return subTag;
                }
            }
        }
        return null;
    }

    private void syncWhere(@NotNull XmlTag whereTagOwner,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        List<XmlTag> nestedWhere = findNestedTagsByName(whereTagOwner, "where");
        XmlTag targetTag = nestedWhere.isEmpty() ? whereTagOwner : nestedWhere.get(0);
        syncConditionContainers(targetTag, selectedFields, allFieldsInOrder);
    }

    private boolean containsWhereCondition(@NotNull String body, @NotNull FieldInfo field) {
        return Pattern
                .compile("(?i)(^|[^A-Za-z0-9_`])`?" + Pattern.quote(NameUtil.camelToSnake(field.name())) + "`?\\s*=")
                .matcher(body).find();
    }

    private void syncBaseColumnList(@NotNull XmlTag sqlTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        String body = sqlTag.getValue().getText();
        String baseIndent = detectEntryIndent(body);
        boolean changed = false;

        for (FieldInfo field : selectedFields) {
            if (containsColumnEntry(body, field)) {
                continue;
            }
            String entry = NameUtil.camelToSnake(field.name()) + ",";
            body = insertPlainEntryByFieldOrder(body, entry, baseIndent, allFieldsInOrder, field,
                    this::columnPatternFor);
            changed = true;
        }

        if (changed) {
            sqlTag.getValue().setText(body);
        }
    }

    /**
     * 同步 resultMap 的 result 标签，保持字段顺序。
     */
    private void syncResultMap(@NotNull XmlTag resultMapTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        List<XmlTag> resultTags = findNestedTagsByName(resultMapTag, "result");

        // 检测是否已有嵌套的 if 标签
        boolean hasIfStyle = resultTags.stream().anyMatch(tag -> !findNestedTagsByName(tag, "if").isEmpty());

        // 检测缩进
        String indent = detectChildIndent(resultMapTag);
        String childIndent = indent + IndentUtil.detectIndentUnit(resultMapTag.getText());

        String body = resultMapTag.getValue().getText();
        boolean changed = false;

        for (FieldInfo field : selectedFields) {
            if (containsResultEntry(body, field)) {
                continue;
            }

            String column = NameUtil.camelToSnake(field.name());
            String property = field.name();
            String entry;
            if (hasIfStyle) {
                String testExpr = buildIfTest(field);
                entry = String.format("\n%s<if test=\"%s\"> %s = %s, </if>",
                        childIndent, testExpr, column, buildParamPlaceholder(field));
            } else {
                entry = "\n" + childIndent + XmlMappingRenderUtil.buildResultTag(column, property, resolveTypeMapping(field));
            }

            body = insertResultEntryByFieldOrder(body, entry, allFieldsInOrder, field);
            changed = true;
        }

        if (changed) {
            resultMapTag.getValue().setText(body);
        }
    }

    private String insertResultEntryByFieldOrder(@NotNull String body,
            @NotNull String entry,
            @NotNull List<FieldInfo> orderedFields,
            @NotNull FieldInfo currentField) {
        int currentIndex = orderedFields.indexOf(currentField);
        if (currentIndex < 0) {
            return body + entry;
        }

        int anchorLineStart = -1;
        for (int i = currentIndex + 1; i < orderedFields.size(); i++) {
            Pattern pattern = resultPatternFor(orderedFields.get(i));
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                anchorLineStart = findLineStart(body, matcher.start());
                break;
            }
        }

        if (anchorLineStart < 0) {
            return body + entry;
        }

        return body.substring(0, anchorLineStart) + entry + body.substring(anchorLineStart);
    }

    private boolean containsResultEntry(@NotNull String body, @NotNull FieldInfo field) {
        return resultPatternFor(field).matcher(body).find();
    }

    private Pattern resultPatternFor(@NotNull FieldInfo field) {
        String column = NameUtil.camelToSnake(field.name());
        String property = field.name();
        return XmlFieldSyncSupport.resultMappingPattern(property, column);
    }

    private void mergeInsertTrim(@NotNull XmlTag columnTrim,
            @NotNull XmlTag valueTrim,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        boolean columnHasIfStyle = hasNestedTagByName(columnTrim, "if");
        boolean valueHasIfStyle = hasNestedTagByName(valueTrim, "if");

        if (columnHasIfStyle || valueHasIfStyle) {
            mergeInsertTrimWithIfTags(columnTrim, valueTrim, selectedFields, allFieldsInOrder);
            return;
        }

        String columnBody = columnTrim.getValue().getText();
        String valueBody = valueTrim.getValue().getText();
        String columnIndent = detectEntryIndent(columnBody);
        String valueIndent = detectEntryIndent(valueBody);

        boolean changed = false;
        for (FieldInfo field : selectedFields) {
            if (!containsColumnEntry(columnBody, field)) {
                columnBody = insertPlainEntryByFieldOrder(
                        columnBody,
                        NameUtil.camelToSnake(field.name()) + ",",
                        columnIndent,
                        allFieldsInOrder,
                        field,
                        this::columnPatternFor);
                changed = true;
            }
            if (!containsValueEntry(valueBody, field)) {
                valueBody = insertPlainEntryByFieldOrder(
                        valueBody,
                        buildParamPlaceholder(field) + ",",
                        valueIndent,
                        allFieldsInOrder,
                        field,
                        this::valuePatternFor);
                changed = true;
            }
        }

        if (changed) {
            columnTrim.getValue().setText(columnBody);
            valueTrim.getValue().setText(valueBody);
        }
    }

    private void mergeSetTag(@NotNull XmlTag setTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        boolean hasIfStyle = hasNestedTagByName(setTag, "if");
        if (hasIfStyle) {
            mergeSetTagWithIfTags(setTag, selectedFields, allFieldsInOrder);
            return;
        }

        String body = setTag.getValue().getText();
        String indent = detectEntryIndent(body);
        boolean changed = false;
        for (FieldInfo field : selectedFields) {
            if (containsUpdateAssignment(body, field)) {
                continue;
            }
            String entry = NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field) + ",";
            body = insertPlainEntryByFieldOrder(body, entry, indent, allFieldsInOrder, field,
                    this::assignmentPatternFor);
            changed = true;
        }

        if (changed) {
            setTag.getValue().setText(body);
        }
    }

    private boolean hasNestedTagByName(@NotNull XmlTag tag, @NotNull String tagName) {
        return !findNestedTagsByName(tag, tagName).isEmpty();
    }

    private void mergeInsertTrimWithIfTags(@NotNull XmlTag columnTrim,
            @NotNull XmlTag valueTrim,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        String columnIndent = detectChildIndent(columnTrim);
        String valueIndent = detectChildIndent(valueTrim);
        String columnChildIndent = columnIndent + IndentUtil.detectIndentUnit(columnTrim.getText());
        String valueChildIndent = valueIndent + IndentUtil.detectIndentUnit(valueTrim.getText());

        for (FieldInfo field : selectedFields) {
            if (!containsColumnEntry(columnTrim.getText(), field)) {
                XmlTag ifTag = createIfTag(columnTrim, buildIfTest(field), NameUtil.camelToSnake(field.name()) + ",",
                        columnChildIndent);
                insertIfTagByFieldOrder(columnTrim, ifTag, allFieldsInOrder, field, this::containsColumnEntry);
            }

            if (!containsValueEntry(valueTrim.getText(), field)) {
                XmlTag ifTag = createIfTag(valueTrim, buildIfTest(field), buildParamPlaceholder(field) + ",",
                        valueChildIndent);
                insertIfTagByFieldOrder(valueTrim, ifTag, allFieldsInOrder, field, this::containsValueEntry);
            }
        }
    }

    private boolean mergeInsertWithoutTrimIf(@NotNull XmlTag insertTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        List<XmlTag> directIfTags = Arrays.stream(insertTag.getSubTags())
                .filter(tag -> "if".equalsIgnoreCase(tag.getName()))
                .toList();
        if (directIfTags.isEmpty()) {
            return false;
        }

        int valueGroupStart = -1;
        for (int i = 0; i < directIfTags.size(); i++) {
            if (directIfTags.get(i).getText().contains("#{")) {
                valueGroupStart = i;
                break;
            }
        }
        if (valueGroupStart <= 0) {
            return false;
        }

        List<XmlTag> columnGroup = directIfTags.subList(0, valueGroupStart);
        List<XmlTag> valueGroup = directIfTags.subList(valueGroupStart, directIfTags.size());
        String columnIndent = detectChildIndent(insertTag) + IndentUtil.detectIndentUnit(insertTag.getText());
        String valueIndent = columnIndent;

        for (FieldInfo field : selectedFields) {
            if (!containsInIfGroup(columnGroup, field, this::containsColumnEntry)) {
                XmlTag ifTag = createIfTag(insertTag, buildIfTest(field), NameUtil.camelToSnake(field.name()) + ",",
                        columnIndent);
                insertIfTagByFieldOrderInGroup(insertTag, ifTag, allFieldsInOrder, field, this::containsColumnEntry,
                        columnGroup, valueGroupStart > 0 ? valueGroup.get(0) : null);
                columnGroup = Arrays.stream(insertTag.getSubTags())
                        .filter(tag -> "if".equalsIgnoreCase(tag.getName()) && !tag.getText().contains("#{"))
                        .toList();
            }

            if (!containsInIfGroup(valueGroup, field, this::containsValueEntry)) {
                XmlTag ifTag = createIfTag(insertTag, buildIfTest(field), buildParamPlaceholder(field) + ",",
                        valueIndent);
                insertIfTagByFieldOrderInGroup(insertTag, ifTag, allFieldsInOrder, field, this::containsValueEntry,
                        valueGroup, null);
                valueGroup = Arrays.stream(insertTag.getSubTags())
                        .filter(tag -> "if".equalsIgnoreCase(tag.getName()) && tag.getText().contains("#{"))
                        .toList();
            }
        }
        return true;
    }

    private void mergeSetTagWithIfTags(@NotNull XmlTag setTag,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        String indent = detectChildIndent(setTag);
        String childIndent = indent + IndentUtil.detectIndentUnit(setTag.getText());

        for (FieldInfo field : selectedFields) {
            if (containsUpdateAssignment(setTag.getText(), field)) {
                continue;
            }

            String assignment = NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field) + ",";
            XmlTag ifTag = createIfTag(setTag, buildIfTest(field), assignment, childIndent);
            insertIfTagByFieldOrder(setTag, ifTag, allFieldsInOrder, field, this::containsUpdateAssignment);
        }
    }

    private void syncAssignmentContainers(@NotNull XmlTag root,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        List<List<Integer>> paths = DynamicSqlStructureSupport.findConditionalContainerPaths(new PsiXmlTagView(root));
        for (List<Integer> path : paths) {
            XmlTag container = resolveTagByElementPath(root, path);
            if (container == null) {
                continue;
            }
            mergeAssignmentContainer(container, selectedFields, allFieldsInOrder);
        }
    }

    private void syncConditionContainers(@NotNull XmlTag root,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        List<List<Integer>> paths = DynamicSqlStructureSupport.findConditionalContainerPaths(new PsiXmlTagView(root));
        for (List<Integer> path : paths) {
            XmlTag container = resolveTagByElementPath(root, path);
            if (container == null) {
                continue;
            }
            mergeConditionContainer(container, selectedFields, allFieldsInOrder);
        }
    }

    private void mergeAssignmentContainer(@NotNull XmlTag container,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        if (hasDirectChildTagByName(container, "if")) {
            mergeGenericIfContainer(container,
                    selectedFields,
                    allFieldsInOrder,
                    field -> NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field) + ",",
                    this::containsUpdateAssignment);
            return;
        }
        mergePlainTextContainer(container,
                selectedFields,
                allFieldsInOrder,
                field -> NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field) + ",",
                this::containsUpdateAssignment,
                this::assignmentPatternFor);
    }

    private void mergeConditionContainer(@NotNull XmlTag container,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder) {
        if (hasDirectChildTagByName(container, "if")) {
            mergeGenericIfContainer(container,
                    selectedFields,
                    allFieldsInOrder,
                    field -> "and " + NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field),
                    this::containsWhereCondition);
            return;
        }
        mergePlainTextContainer(container,
                selectedFields,
                allFieldsInOrder,
                field -> "and " + NameUtil.camelToSnake(field.name()) + " = " + buildParamPlaceholder(field),
                this::containsWhereCondition,
                this::columnPatternFor);
    }

    private void mergeGenericIfContainer(@NotNull XmlTag container,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder,
            @NotNull Function<FieldInfo, String> lineBuilder,
            @NotNull BiPredicate<String, FieldInfo> matcher) {
        String indent = detectChildIndent(container);
        String childIndent = indent + IndentUtil.detectIndentUnit(container.getText());

        for (FieldInfo field : selectedFields) {
            if (matcher.test(container.getText(), field)) {
                continue;
            }
            XmlTag ifTag = createIfTag(container, buildIfTest(field), lineBuilder.apply(field), childIndent);
            insertIfTagByFieldOrder(container, ifTag, allFieldsInOrder, field, matcher);
        }
    }

    private void mergePlainTextContainer(@NotNull XmlTag container,
            @NotNull List<FieldInfo> selectedFields,
            @NotNull List<FieldInfo> allFieldsInOrder,
            @NotNull Function<FieldInfo, String> lineBuilder,
            @NotNull BiPredicate<String, FieldInfo> matcher,
            @NotNull Function<FieldInfo, Pattern> patternProvider) {
        String body = container.getValue().getText();
        String indent = detectEntryIndent(body);
        boolean changed = false;
        for (FieldInfo field : selectedFields) {
            if (matcher.test(body, field)) {
                continue;
            }
            body = insertPlainEntryByFieldOrder(body, lineBuilder.apply(field), indent, allFieldsInOrder, field, patternProvider);
            changed = true;
        }
        if (changed) {
            container.getValue().setText(body);
        }
    }

    private boolean hasDirectChildTagByName(@NotNull XmlTag tag, @NotNull String name) {
        for (XmlTag subTag : tag.getSubTags()) {
            if (name.equalsIgnoreCase(subTag.getName())) {
                return true;
            }
        }
        return false;
    }

    private void insertIfTagByFieldOrder(@NotNull XmlTag parent,
            @NotNull XmlTag newIfTag,
            @NotNull List<FieldInfo> orderedFields,
            @NotNull FieldInfo currentField,
            @NotNull BiPredicate<String, FieldInfo> matcher) {
        int currentIndex = orderedFields.indexOf(currentField);
        XmlTag anchor = findNextIfAnchor(parent, orderedFields, currentIndex, matcher);
        if (anchor == null) {
            parent.addSubTag(newIfTag, false);
            return;
        }
        parent.addBefore(newIfTag, anchor);
    }

    private void insertIfTagByFieldOrderInGroup(@NotNull XmlTag parent,
            @NotNull XmlTag newIfTag,
            @NotNull List<FieldInfo> orderedFields,
            @NotNull FieldInfo currentField,
            @NotNull BiPredicate<String, FieldInfo> matcher,
            @NotNull List<XmlTag> groupTags,
            XmlTag fallbackAnchor) {
        int currentIndex = orderedFields.indexOf(currentField);
        XmlTag anchor = findNextIfAnchorInGroup(groupTags, orderedFields, currentIndex, matcher);
        if (anchor != null) {
            parent.addBefore(newIfTag, anchor);
            return;
        }

        if (!groupTags.isEmpty()) {
            parent.addAfter(newIfTag, groupTags.get(groupTags.size() - 1));
            return;
        }

        if (fallbackAnchor != null) {
            parent.addBefore(newIfTag, fallbackAnchor);
            return;
        }
        parent.addSubTag(newIfTag, false);
    }

    private boolean containsInIfGroup(@NotNull List<XmlTag> groupTags,
            @NotNull FieldInfo field,
            @NotNull BiPredicate<String, FieldInfo> matcher) {
        for (XmlTag tag : groupTags) {
            if (matcher.test(tag.getText(), field)) {
                return true;
            }
        }
        return false;
    }

    private XmlTag findNextIfAnchor(@NotNull XmlTag parent,
            @NotNull List<FieldInfo> orderedFields,
            int currentIndex,
            @NotNull BiPredicate<String, FieldInfo> matcher) {
        XmlTag[] subTags = parent.getSubTags();
        for (int i = currentIndex + 1; i < orderedFields.size(); i++) {
            FieldInfo candidateField = orderedFields.get(i);
            for (XmlTag subTag : subTags) {
                if (!"if".equalsIgnoreCase(subTag.getName())) {
                    continue;
                }
                if (matcher.test(subTag.getText(), candidateField)) {
                    return subTag;
                }
            }
        }
        return null;
    }

    private XmlTag findNextIfAnchorInGroup(@NotNull List<XmlTag> groupTags,
            @NotNull List<FieldInfo> orderedFields,
            int currentIndex,
            @NotNull BiPredicate<String, FieldInfo> matcher) {
        if (currentIndex < 0) {
            return null;
        }
        for (int i = currentIndex + 1; i < orderedFields.size(); i++) {
            FieldInfo candidateField = orderedFields.get(i);
            for (XmlTag groupTag : groupTags) {
                if (matcher.test(groupTag.getText(), candidateField)) {
                    return groupTag;
                }
            }
        }
        return null;
    }

    private XmlTag createIfTag(@NotNull XmlTag parent,
            @NotNull String testExpr,
            @NotNull String bodyLine,
            @NotNull String bodyIndent) {
        XmlTag ifTag = parent.createChildTag("if", null, "\n" + bodyIndent + bodyLine + "\n", false);
        ifTag.setAttribute("test", testExpr);
        return ifTag;
    }

    private String detectChildIndent(@NotNull XmlTag tag) {
        for (XmlTag subTag : tag.getSubTags()) {
            String text = subTag.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            int idx = 0;
            while (idx < text.length() && Character.isWhitespace(text.charAt(idx))) {
                idx++;
            }
            if (idx > 0) {
                return text.substring(0, idx);
            }
        }
        return IndentUtil.detectIndentUnit(tag.getText());
    }

    private boolean containsColumnEntry(@NotNull String body, @NotNull FieldInfo field) {
        return columnPatternFor(field).matcher(body).find();
    }

    private boolean containsValueEntry(@NotNull String body, @NotNull FieldInfo field) {
        return valuePatternFor(field).matcher(body).find();
    }

    private boolean containsUpdateAssignment(@NotNull String body, @NotNull FieldInfo field) {
        if (assignmentPatternFor(field).matcher(body).find()) {
            return true;
        }
        return containsValueEntry(body, field);
    }

    private String buildIfTest(@NotNull FieldInfo field) {
        String canonicalType = field.psiField().getType().getCanonicalText();
        if (isNumericPrimitive(canonicalType)) {
            return field.name() + " != 0";
        }
        if (isNumericWrapper(canonicalType)) {
            return field.name() + " != null and " + field.name() + " != 0";
        }
        if (isStringField(field)) {
            return field.name() + " != null and " + field.name() + " != ''";
        }
        return field.name() + " != null";
    }

    private boolean isStringField(@NotNull FieldInfo field) {
        String canonicalType = field.psiField().getType().getCanonicalText();
        return "java.lang.String".equals(canonicalType) || "String".equals(field.type());
    }

    private boolean isNumericPrimitive(@NotNull String canonicalType) {
        return switch (canonicalType) {
            case "byte", "short", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    private boolean isNumericWrapper(@NotNull String canonicalType) {
        return switch (canonicalType) {
            case "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
                    "java.lang.Long", "java.lang.Float", "java.lang.Double",
                    "java.math.BigDecimal" ->
                true;
            default -> false;
        };
    }

    private String appendEntry(@NotNull String body, @NotNull String entry, @NotNull String baseIndent) {
        XmlFormatSettingsUtil.ResolvedXmlFormat format = resolveXmlFormat(body);
        String normalized = body.stripTrailing();
        String normalizedEntry = XmlFormatSettingsUtil.normalizeEntry(entry);

        if (normalized.isBlank()) {
            if (format.lineBreakStyle() == XmlFormatSettingsUtil.LineBreakStyle.SINGLE_LINE) {
                return normalizedEntry;
            }
            return "\n" + baseIndent + XmlFormatSettingsUtil.renderEntry(normalizedEntry, format, true) + "\n";
        }

        if (format.lineBreakStyle() == XmlFormatSettingsUtil.LineBreakStyle.SINGLE_LINE) {
            return normalized + (normalized.endsWith(",") ? " " : ", ") + normalizedEntry;
        }

        boolean firstEntry = !containsAnyConfiguredEntry(body);
        return normalized + "\n" + baseIndent + XmlFormatSettingsUtil.renderEntry(normalizedEntry, format, firstEntry) + "\n";
    }

    private String insertPlainEntryByFieldOrder(@NotNull String body,
            @NotNull String entry,
            @NotNull String baseIndent,
            @NotNull List<FieldInfo> orderedFields,
            @NotNull FieldInfo currentField,
            @NotNull Function<FieldInfo, Pattern> patternProvider) {
        XmlFormatSettingsUtil.ResolvedXmlFormat format = resolveXmlFormat(body);
        String normalizedEntry = XmlFormatSettingsUtil.normalizeEntry(entry);
        int currentIndex = orderedFields.indexOf(currentField);
        if (currentIndex < 0) {
            return appendEntry(body, normalizedEntry, baseIndent);
        }

        int anchorOffset = -1;
        int anchorLineStart = -1;
        for (int i = currentIndex + 1; i < orderedFields.size(); i++) {
            Matcher matcher = patternProvider.apply(orderedFields.get(i)).matcher(body);
            if (matcher.find()) {
                anchorOffset = matcher.start();
                anchorLineStart = findLineStart(body, matcher.start());
                break;
            }
        }

        if (anchorLineStart < 0) {
            return appendEntry(body, normalizedEntry, baseIndent);
        }

        if (format.lineBreakStyle() == XmlFormatSettingsUtil.LineBreakStyle.SINGLE_LINE) {
            return insertInlineEntry(body, normalizedEntry, anchorOffset);
        }

        boolean hasPreviousEntry = hasPreviousConfiguredEntry(body, orderedFields, currentIndex, patternProvider);
        if (format.commaStyle() == XmlFormatSettingsUtil.CommaStyle.LEADING) {
            return insertLeadingMultilineEntry(body, normalizedEntry, baseIndent, anchorLineStart, hasPreviousEntry);
        }

        String prefix = body.substring(0, anchorLineStart);
        String suffix = body.substring(anchorLineStart);
        if (!prefix.endsWith("\n")) {
            prefix = prefix + "\n";
        }
        return prefix + baseIndent + XmlFormatSettingsUtil.renderEntry(normalizedEntry, format, false) + "\n" + suffix;
    }

    private int findLineStart(@NotNull String text, int offset) {
        int idx = Math.max(0, Math.min(offset, text.length()));
        while (idx > 0 && text.charAt(idx - 1) != '\n') {
            idx--;
        }
        return idx;
    }

    private Pattern columnPatternFor(@NotNull FieldInfo field) {
        String column = NameUtil.camelToSnake(field.name());
        return Pattern.compile("(?i)((^|[^A-Za-z0-9_`])`?" + Pattern.quote(column) + "`?\\s*,)|(,\\s*`?"
                + Pattern.quote(column) + "`?(?=$|\\s|\\n))");
    }

    private Pattern valuePatternFor(@NotNull FieldInfo field) {
        return Pattern.compile("#\\{\\s*" + Pattern.quote(field.name()) + "\\b");
    }

    private Pattern assignmentPatternFor(@NotNull FieldInfo field) {
        String column = NameUtil.camelToSnake(field.name());
        return Pattern.compile("(?i)(^|[^A-Za-z0-9_`])`?" + Pattern.quote(column) + "`?\\s*=");
    }

    private String detectEntryIndent(@NotNull String body) {
        String[] lines = body.split("\\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int idx = 0;
            while (idx < line.length() && Character.isWhitespace(line.charAt(idx))) {
                idx++;
            }
            if (idx > 0) {
                return line.substring(0, idx);
            }
        }
        return IndentUtil.detectIndentUnit(body);
    }

    private String buildParamPlaceholder(@NotNull FieldInfo fieldInfo) {
        return XmlMappingRenderUtil.buildParameterPlaceholder(fieldInfo.name(), resolveTypeMapping(fieldInfo));
    }

    private @NotNull TypeMappingUtil.ResolvedTypeMapping resolveTypeMapping(@NotNull FieldInfo fieldInfo) {
        return TypeMappingUtil.resolve(settingsState(), fieldInfo.psiField().getType().getCanonicalText(), fieldInfo.jdbcType());
    }

    private @NotNull MyBatisFieldSyncSettings.State settingsState() {
        return MyBatisFieldSyncSettings.getInstance(project).getState();
    }

    private @NotNull XmlFormatSettingsUtil.ResolvedXmlFormat resolveXmlFormat(@NotNull String body) {
        return XmlFormatSettingsUtil.resolve(settingsState(), body);
    }

    private boolean hasPreviousConfiguredEntry(@NotNull String body,
            @NotNull List<FieldInfo> orderedFields,
            int currentIndex,
            @NotNull Function<FieldInfo, Pattern> patternProvider) {
        for (int i = 0; i < currentIndex; i++) {
            if (patternProvider.apply(orderedFields.get(i)).matcher(body).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyConfiguredEntry(@NotNull String body) {
        return body.lines().anyMatch(line -> !line.isBlank());
    }

    private @NotNull String insertInlineEntry(@NotNull String body, @NotNull String entry, int anchorOffset) {
        String prefix = body.substring(0, anchorOffset).stripTrailing();
        String suffix = body.substring(anchorOffset).stripLeading();
        if (prefix.isBlank()) {
            return entry + ", " + suffix;
        }
        String inserted = prefix + (prefix.endsWith(",") ? " " : ", ") + entry;
        if (suffix.isBlank()) {
            return inserted;
        }
        return inserted + ", " + suffix;
    }

    private @NotNull String insertLeadingMultilineEntry(@NotNull String body,
            @NotNull String entry,
            @NotNull String baseIndent,
            int anchorLineStart,
            boolean hasPreviousEntry) {
        String prefix = body.substring(0, anchorLineStart);
        String suffix = body.substring(anchorLineStart);
        if (!prefix.endsWith("\n")) {
            prefix = prefix + "\n";
        }
        String renderedEntry = baseIndent
                + XmlFormatSettingsUtil.renderEntry(
                        entry,
                        new XmlFormatSettingsUtil.ResolvedXmlFormat(
                                resolveXmlFormat(body).indentUnit(),
                                XmlFormatSettingsUtil.LineBreakStyle.MULTI_LINE,
                                XmlFormatSettingsUtil.CommaStyle.LEADING),
                        !hasPreviousEntry)
                + "\n";
        if (!hasPreviousEntry) {
            suffix = addLeadingCommaToLine(suffix);
        }
        return prefix + renderedEntry + suffix;
    }

    private @NotNull String addLeadingCommaToLine(@NotNull String text) {
        int lineEnd = text.indexOf('\n');
        String firstLine = lineEnd >= 0 ? text.substring(0, lineEnd) : text;
        String remainder = lineEnd >= 0 ? text.substring(lineEnd) : "";
        String trimmed = firstLine.stripLeading();
        if (trimmed.startsWith(",")) {
            return text;
        }
        int whitespace = 0;
        while (whitespace < firstLine.length() && Character.isWhitespace(firstLine.charAt(whitespace))) {
            whitespace++;
        }
        String updated = firstLine.substring(0, whitespace) + ", " + firstLine.substring(whitespace).stripLeading();
        return updated + remainder;
    }

    private XmlTag resolveTagByElementPath(@NotNull XmlTag root, @NotNull List<Integer> path) {
        XmlTag current = root;
        for (Integer index : path) {
            XmlTag[] children = current.getSubTags();
            if (index < 0 || index >= children.length) {
                return null;
            }
            current = children[index];
        }
        return current;
    }

    /**
     * 通过精确文件名收集 XML，并保持顺序与唯一性。
     */
    private void addByFileName(@NotNull Set<XmlFile> files, @NotNull String fileName,
            @NotNull GlobalSearchScope scope) {
        var virtualFiles = FilenameIndex.getVirtualFilesByName(project, fileName, scope);
        for (var vf : virtualFiles) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
            if (psiFile instanceof XmlFile xmlFile) {
                files.add(xmlFile);
            }
        }
    }

    /**
     * 同时支持实体名与常规 Mapper 名称的 namespace 匹配。
     */
    private boolean namespaceMatches(@NotNull String className, String qualifiedName, @NotNull String namespace) {
        if (namespace.equals(className) || namespace.endsWith("." + className)) {
            return true;
        }
        if (qualifiedName != null && namespace.equals(qualifiedName)) {
            return true;
        }
        String mapperName = className + "Mapper";
        if (namespace.equals(mapperName) || namespace.endsWith("." + mapperName)) {
            return true;
        }
        return qualifiedName != null && namespace.equals(qualifiedName + "Mapper");
    }

    /**
     * 递归收集带显式 id 的 SQL 语句标签。
     */
    private void collectStatementsRecursively(@NotNull XmlTag parent, @NotNull List<StatementInfo> result) {
        for (XmlTag subTag : parent.getSubTags()) {
            String id = subTag.getAttributeValue("id");
            if (id != null && !id.isBlank() && STATEMENT_TAGS.contains(subTag.getName().toLowerCase(Locale.ROOT))) {
                result.add(new StatementInfo(id, subTag.getName(), subTag));
            }
            collectStatementsRecursively(subTag, result);
        }
    }

    /**
     * 使用 DFS 按名称查找嵌套标签。
     */
    private @NotNull List<XmlTag> findNestedTagsByName(@NotNull XmlTag root, @NotNull String tagName) {
        List<XmlTag> result = new ArrayList<>();
        findNestedTagsByName(root, tagName, result);
        return result;
    }

    public record ReverseGenerationResult(@NotNull List<XmlFieldSyncSupport.XmlFieldDraft> newDrafts,
                                          @NotNull List<String> conflicts) {
    }

    private @NotNull Set<String> collectDependencyModuleNames(@NotNull Module sourceModule) {
        Set<String> dependencyModuleNames = new HashSet<>();
        OrderEnumerator.orderEntries(sourceModule)
                .recursively()
                .withoutSdk()
                .withoutLibraries()
                .forEachModule(module -> {
                    dependencyModuleNames.add(module.getName());
                    return true;
                });
        dependencyModuleNames.remove(sourceModule.getName());
        return dependencyModuleNames;
    }

    private String resolveModuleName(@NotNull ProjectFileIndex fileIndex, @NotNull XmlFile xmlFile) {
        VirtualFile virtualFile = xmlFile.getVirtualFile();
        if (virtualFile == null) {
            return "";
        }
        Module module = fileIndex.getModuleForFile(virtualFile);
        return module == null ? "" : module.getName();
    }

    private String resolveXmlPath(@NotNull XmlFile xmlFile) {
        VirtualFile virtualFile = xmlFile.getVirtualFile();
        return virtualFile == null ? xmlFile.getName() : virtualFile.getPath();
    }

    private static int resolveModulePriority(String candidateModuleName,
            String sourceModuleName,
            @NotNull Set<String> dependencyModuleNames) {
        if (sourceModuleName == null || sourceModuleName.isBlank()) {
            return 2;
        }
        if (Objects.equals(sourceModuleName, candidateModuleName)) {
            return 0;
        }
        if (candidateModuleName != null && dependencyModuleNames.contains(candidateModuleName)) {
            return 1;
        }
        return 2;
    }

    private static @NotNull String normalizeSortValue(String value) {
        return value == null ? "" : value;
    }

    private void findNestedTagsByName(@NotNull XmlTag root, @NotNull String tagName, @NotNull List<XmlTag> out) {
        for (XmlTag subTag : root.getSubTags()) {
            if (tagName.equalsIgnoreCase(subTag.getName())) {
                out.add(subTag);
            }
            findNestedTagsByName(subTag, tagName, out);
        }
    }

    private static final class PsiXmlTagView implements DynamicSqlStructureSupport.TagView {
        private final XmlTag tag;

        private PsiXmlTagView(@NotNull XmlTag tag) {
            this.tag = tag;
        }

        @Override
        public @NotNull String name() {
            return tag.getName();
        }

        @Override
        public @NotNull String text() {
            return tag.getText();
        }

        @Override
        public @NotNull List<PsiXmlTagView> children() {
            List<PsiXmlTagView> children = new ArrayList<>();
            for (XmlTag subTag : tag.getSubTags()) {
                children.add(new PsiXmlTagView(subTag));
            }
            return children;
        }

        @Override
        public String attribute(@NotNull String name) {
            return tag.getAttributeValue(name);
        }
    }
}
