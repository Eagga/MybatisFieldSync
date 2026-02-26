package com.eagga.mybatisfieldsync.service;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.model.StatementInfo;
import com.eagga.mybatisfieldsync.model.SyncException;
import com.eagga.mybatisfieldsync.util.IndentUtil;
import com.eagga.mybatisfieldsync.util.JdbcTypeUtil;
import com.eagga.mybatisfieldsync.util.NameUtil;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
    private static final Pattern INSERT_VALUES_PATTERN = Pattern.compile("(?is)\\(\\s*[^()]*\\)\\s*values\\s*\\(\\s*[^()]*\\)");
    private static final Set<String> STATEMENT_TAGS = new HashSet<>(List.of("insert", "update", "delete", "select", "sql"));
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

                result.put(field.getName(), new FieldInfo(
                        field,
                        field.getName(),
                        field.getType().getPresentableText(),
                        JdbcTypeUtil.resolveJdbcType(field.getType().getCanonicalText()),
                        cursor.getName() == null ? "" : cursor.getName(),
                        inherited
                ));
            }

            if (!includeInherited) {
                break;
            }
            cursor = cursor.getSuperClass();
            if (cursor != null && "java.lang.Object".equals(cursor.getQualifiedName())) {
                break;
            }
        }

        return new ArrayList<>(result.values());
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

        return new ArrayList<>(files);
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

    /**
     * 在写命令中执行单个 Statement 同步，确保支持 IDE 的撤销/重做。
     */
    public void syncInWriteCommand(@NotNull XmlFile xmlFile,
                                   @NotNull StatementInfo statementInfo,
                                   @NotNull List<FieldInfo> selectedFields,
                                   @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
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
                xmlFile
        );

        if (exceptionRef.get() != null) {
            throw exceptionRef.get();
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
            syncInsert(statementTag, selectedFields, allFieldsInOrder);
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

        throw new SyncException(MyBatisFieldSyncBundle.message("notify.unsupported", id));
    }

    /**
     * 同步 insert 语句：
     * - 优先：对 trim（列/值）增量补齐，保持列和值位置对应
     * - 回退：替换普通 SQL 文本中的 "(...) VALUES (...)" 片段
     */
    private void syncInsert(@NotNull XmlTag insertTag,
                            @NotNull List<FieldInfo> selectedFields,
                            @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
        List<XmlTag> trimTags = findNestedTagsByName(insertTag, "trim");
        if (trimTags.size() >= 2) {
            mergeInsertTrim(trimTags.get(0), trimTags.get(1), selectedFields, allFieldsInOrder);
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
    private void syncUpdate(@NotNull XmlTag updateTag,
                            @NotNull List<FieldInfo> selectedFields,
                            @NotNull List<FieldInfo> allFieldsInOrder) throws SyncException {
        List<XmlTag> setTags = findNestedTagsByName(updateTag, "set");
        if (!setTags.isEmpty()) {
            mergeSetTag(setTags.get(0), selectedFields, allFieldsInOrder);
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
        String replacement = matcher.group(1) + "\n" + indent + String.join(",\n" + indent, assignments) + "\n" + matcher.group(3);
        String updated = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        updateTag.getValue().setText(updated);
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
            body = insertPlainEntryByFieldOrder(body, entry, baseIndent, allFieldsInOrder, field, this::columnPatternFor);
            changed = true;
        }

        if (changed) {
            sqlTag.getValue().setText(body);
        }
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
                        this::columnPatternFor
                );
                changed = true;
            }
            if (!containsValueEntry(valueBody, field)) {
                valueBody = insertPlainEntryByFieldOrder(
                        valueBody,
                        buildParamPlaceholder(field) + ",",
                        valueIndent,
                        allFieldsInOrder,
                        field,
                        this::valuePatternFor
                );
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
            body = insertPlainEntryByFieldOrder(body, entry, indent, allFieldsInOrder, field, this::assignmentPatternFor);
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
                XmlTag ifTag = createIfTag(columnTrim, buildIfTest(field), NameUtil.camelToSnake(field.name()) + ",", columnChildIndent);
                insertIfTagByFieldOrder(columnTrim, ifTag, allFieldsInOrder, field, this::containsColumnEntry);
            }

            if (!containsValueEntry(valueTrim.getText(), field)) {
                XmlTag ifTag = createIfTag(valueTrim, buildIfTest(field), buildParamPlaceholder(field) + ",", valueChildIndent);
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
                XmlTag ifTag = createIfTag(insertTag, buildIfTest(field), NameUtil.camelToSnake(field.name()) + ",", columnIndent);
                insertIfTagByFieldOrderInGroup(insertTag, ifTag, allFieldsInOrder, field, this::containsColumnEntry, columnGroup, valueGroupStart > 0 ? valueGroup.get(0) : null);
                columnGroup = Arrays.stream(insertTag.getSubTags())
                        .filter(tag -> "if".equalsIgnoreCase(tag.getName()) && !tag.getText().contains("#{"))
                        .toList();
            }

            if (!containsInIfGroup(valueGroup, field, this::containsValueEntry)) {
                XmlTag ifTag = createIfTag(insertTag, buildIfTest(field), buildParamPlaceholder(field) + ",", valueIndent);
                insertIfTagByFieldOrderInGroup(insertTag, ifTag, allFieldsInOrder, field, this::containsValueEntry, valueGroup, null);
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
                 "java.math.BigDecimal" -> true;
            default -> false;
        };
    }

    private String appendEntry(@NotNull String body, @NotNull String entry, @NotNull String baseIndent) {
        String normalized = body.stripTrailing();
        String indentedEntry = indentMultiline(entry, baseIndent);

        if (normalized.isBlank()) {
            return "\n" + indentedEntry + "\n";
        }

        return normalized + "\n" + indentedEntry + "\n";
    }

    private String insertPlainEntryByFieldOrder(@NotNull String body,
                                                @NotNull String entry,
                                                @NotNull String baseIndent,
                                                @NotNull List<FieldInfo> orderedFields,
                                                @NotNull FieldInfo currentField,
                                                @NotNull Function<FieldInfo, Pattern> patternProvider) {
        int currentIndex = orderedFields.indexOf(currentField);
        if (currentIndex < 0) {
            return appendEntry(body, entry, baseIndent);
        }

        int anchorLineStart = -1;
        for (int i = currentIndex + 1; i < orderedFields.size(); i++) {
            Matcher matcher = patternProvider.apply(orderedFields.get(i)).matcher(body);
            if (matcher.find()) {
                anchorLineStart = findLineStart(body, matcher.start());
                break;
            }
        }

        if (anchorLineStart < 0) {
            return appendEntry(body, entry, baseIndent);
        }

        String prefix = body.substring(0, anchorLineStart);
        String suffix = body.substring(anchorLineStart);
        if (!prefix.endsWith("\n")) {
            prefix = prefix + "\n";
        }
        return prefix + baseIndent + entry + "\n" + suffix;
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
        return Pattern.compile("(?i)(^|[^A-Za-z0-9_`])`?" + Pattern.quote(column) + "`?\\s*,");
    }

    private Pattern valuePatternFor(@NotNull FieldInfo field) {
        return Pattern.compile("#\\{\\s*" + Pattern.quote(field.name()) + "\\b");
    }

    private Pattern assignmentPatternFor(@NotNull FieldInfo field) {
        String column = NameUtil.camelToSnake(field.name());
        return Pattern.compile("(?i)(^|[^A-Za-z0-9_`])`?" + Pattern.quote(column) + "`?\\s*=");
    }

    private String indentMultiline(@NotNull String text, @NotNull String indent) {
        String[] lines = text.split("\\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isEmpty()) {
                sb.append(indent).append(lines[i]);
            }
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
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
        return "#{" + fieldInfo.name() + ",jdbcType=" + fieldInfo.jdbcType() + "}";
    }

    /**
     * 通过精确文件名收集 XML，并保持顺序与唯一性。
     */
    private void addByFileName(@NotNull Set<XmlFile> files, @NotNull String fileName, @NotNull GlobalSearchScope scope) {
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

    private void findNestedTagsByName(@NotNull XmlTag root, @NotNull String tagName, @NotNull List<XmlTag> out) {
        for (XmlTag subTag : root.getSubTags()) {
            if (tagName.equalsIgnoreCase(subTag.getName())) {
                out.add(subTag);
            }
            findNestedTagsByName(subTag, tagName, out);
        }
    }
}
