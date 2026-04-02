package com.eagga.mybatisfieldsync.service;

import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.settings.MyBatisFieldSyncSettings;
import com.eagga.mybatisfieldsync.util.MyBatisPlusUtil;
import com.eagga.mybatisfieldsync.util.NameUtil;
import com.eagga.mybatisfieldsync.util.TypeMappingUtil;
import com.eagga.mybatisfieldsync.util.XmlFormatSettingsUtil;
import com.eagga.mybatisfieldsync.util.XmlMappingRenderUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

@Service(Service.Level.PROJECT)
public final class CrudTemplateService {
    private final Project project;

    public CrudTemplateService(Project project) {
        this.project = project;
    }

    public void generateCrudStatements(@NotNull XmlFile xmlFile,
            @NotNull PsiClass entityClass,
            @NotNull List<FieldInfo> fields,
            @NotNull Set<String> templates) {
        WriteCommandAction.runWriteCommandAction(project, "Generate CRUD Templates", null, () -> {
            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) {
                return;
            }

            XmlFormatSettingsUtil.ResolvedXmlFormat format = XmlFormatSettingsUtil.resolve(settingsState(), rootTag.getText());
            String indent = format.indentUnit();
            String tableName = MyBatisPlusUtil.resolveTableName(entityClass);
            MyBatisPlusUtil.PrimaryKey primaryKey = MyBatisPlusUtil.resolvePrimaryKey(fields);

            if (templates.contains("resultMap")) {
                generateResultMap(rootTag, entityClass, fields, indent);
            }
            if (templates.contains("insert")) {
                generateInsert(rootTag, tableName, fields, indent, format);
            }
            if (templates.contains("update")) {
                generateUpdate(rootTag, tableName, fields, primaryKey, indent);
            }
            if (templates.contains("delete")) {
                generateDelete(rootTag, tableName, primaryKey, indent);
            }
            if (templates.contains("select")) {
                generateSelect(rootTag, tableName, fields, primaryKey, indent);
            }
        }, xmlFile);
    }

    private void generateResultMap(@NotNull XmlTag rootTag, @NotNull PsiClass entityClass,
            @NotNull List<FieldInfo> fields, @NotNull String indent) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append(indent).append("<resultMap id=\"BaseResultMap\" type=\"")
                .append(entityClass.getQualifiedName()).append("\">\n");
        for (FieldInfo field : fields) {
            sb.append(indent).append(indent)
                    .append(XmlMappingRenderUtil.buildResultTag(
                            NameUtil.camelToSnake(field.name()),
                            field.name(),
                            resolveFieldMapping(field)))
                    .append("\n");
        }
        sb.append(indent).append("</resultMap>\n");
        rootTag.getValue().setText(rootTag.getValue().getText() + sb);
    }

    private void generateInsert(@NotNull XmlTag rootTag, @NotNull String tableName,
            @NotNull List<FieldInfo> fields,
            @NotNull String indent,
            @NotNull XmlFormatSettingsUtil.ResolvedXmlFormat format) {
        List<String> columns = fields.stream().map(field -> NameUtil.camelToSnake(field.name())).toList();
        List<String> values = fields.stream()
                .map(field -> XmlMappingRenderUtil.buildParameterPlaceholder(field.name(), resolveFieldMapping(field)))
                .toList();

        String sql = "\n" + indent + "<insert id=\"insert\">\n" +
                indent + indent + "INSERT INTO ${tableName}\n" +
                indent + indent + renderWrappedList(columns, indent + indent, format) + "\n" +
                indent + indent + "VALUES\n" +
                indent + indent + renderWrappedList(values, indent + indent, format) + "\n" +
                indent + "</insert>\n";
        rootTag.getValue().setText(rootTag.getValue().getText() + sql);
    }

    private void generateUpdate(@NotNull XmlTag rootTag, @NotNull String tableName,
            @NotNull List<FieldInfo> fields, @NotNull MyBatisPlusUtil.PrimaryKey primaryKey, @NotNull String indent) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append(indent).append("<update id=\"update\">\n");
        sb.append(indent).append(indent).append("UPDATE ${tableName}\n");
        sb.append(indent).append(indent).append("<set>\n");
        for (FieldInfo field : fields) {
            sb.append(indent).append(indent).append(indent)
                    .append("<if test=\"").append(field.name()).append(" != null\">")
                    .append(NameUtil.camelToSnake(field.name())).append(" = ")
                    .append(XmlMappingRenderUtil.buildParameterPlaceholder(field.name(), resolveFieldMapping(field)))
                    .append("},</if>\n");
        }
        TypeMappingUtil.ResolvedTypeMapping primaryKeyMapping = resolvePrimaryKeyMapping(primaryKey);
        sb.append(indent).append(indent).append("</set>\n");
        sb.append(indent).append(indent)
                .append("WHERE ").append(primaryKey.columnName())
                .append(" = ").append(XmlMappingRenderUtil.buildParameterPlaceholder(primaryKey.propertyName(), primaryKeyMapping))
                .append("\n");
        sb.append(indent).append("</update>\n");
        rootTag.getValue().setText(rootTag.getValue().getText() + sb);
    }

    private void generateDelete(@NotNull XmlTag rootTag, @NotNull String tableName,
            @NotNull MyBatisPlusUtil.PrimaryKey primaryKey, @NotNull String indent) {
        TypeMappingUtil.ResolvedTypeMapping primaryKeyMapping = resolvePrimaryKeyMapping(primaryKey);
        String sql = "\n" + indent + "<delete id=\"delete\">\n" +
                indent + indent + "DELETE FROM ${tableName}\n" +
                indent + indent + "WHERE " + primaryKey.columnName()
                + " = " + XmlMappingRenderUtil.buildParameterPlaceholder(primaryKey.propertyName(), primaryKeyMapping) + "\n" +
                indent + "</delete>\n";
        rootTag.getValue().setText(rootTag.getValue().getText() + sql);
    }

    private void generateSelect(@NotNull XmlTag rootTag, @NotNull String tableName,
            @NotNull List<FieldInfo> fields, @NotNull MyBatisPlusUtil.PrimaryKey primaryKey, @NotNull String indent) {
        StringBuilder columns = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                columns.append(", ");
            }
            columns.append(NameUtil.camelToSnake(fields.get(i).name()));
        }
        TypeMappingUtil.ResolvedTypeMapping primaryKeyMapping = resolvePrimaryKeyMapping(primaryKey);

        String sql = "\n" + indent + "<select id=\"selectById\" resultMap=\"BaseResultMap\">\n" +
                indent + indent + "SELECT " + columns + "\n" +
                indent + indent + "FROM ${tableName}\n" +
                indent + indent + "WHERE " + primaryKey.columnName()
                + " = " + XmlMappingRenderUtil.buildParameterPlaceholder(primaryKey.propertyName(), primaryKeyMapping) + "\n" +
                indent + "</select>\n";
        rootTag.getValue().setText(rootTag.getValue().getText() + sql);
    }

    private @NotNull MyBatisFieldSyncSettings.State settingsState() {
        return MyBatisFieldSyncSettings.getInstance(project).getState();
    }

    private @NotNull TypeMappingUtil.ResolvedTypeMapping resolveFieldMapping(@NotNull FieldInfo field) {
        return TypeMappingUtil.resolve(settingsState(), field.psiField().getType().getCanonicalText(), field.jdbcType());
    }

    private @NotNull TypeMappingUtil.ResolvedTypeMapping resolvePrimaryKeyMapping(
            @NotNull MyBatisPlusUtil.PrimaryKey primaryKey) {
        return TypeMappingUtil.resolve(settingsState(), primaryKey.javaType(), primaryKey.jdbcType());
    }

    private @NotNull String renderWrappedList(@NotNull List<String> items,
            @NotNull String currentIndent,
            @NotNull XmlFormatSettingsUtil.ResolvedXmlFormat format) {
        if (items.isEmpty()) {
            return "()";
        }
        if (format.lineBreakStyle() == XmlFormatSettingsUtil.LineBreakStyle.SINGLE_LINE) {
            return "(" + String.join(", ", items) + ")";
        }
        String itemIndent = currentIndent + format.indentUnit();
        StringBuilder builder = new StringBuilder("(\n");
        for (int i = 0; i < items.size(); i++) {
            builder.append(itemIndent)
                    .append(XmlFormatSettingsUtil.renderEntry(items.get(i), format, i == 0))
                    .append("\n");
        }
        builder.append(currentIndent).append(")");
        return builder.toString();
    }
}
