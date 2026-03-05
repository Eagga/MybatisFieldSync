package com.eagga.mybatisfieldsync.service;

import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.util.IndentUtil;
import com.eagga.mybatisfieldsync.util.NameUtil;
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

            String indent = IndentUtil.detectIndentUnit(rootTag.getText());
            String tableName = NameUtil.camelToSnake(entityClass.getName());

            if (templates.contains("resultMap")) {
                generateResultMap(rootTag, entityClass, fields, indent);
            }
            if (templates.contains("insert")) {
                generateInsert(rootTag, tableName, fields, indent);
            }
            if (templates.contains("update")) {
                generateUpdate(rootTag, tableName, fields, indent);
            }
            if (templates.contains("delete")) {
                generateDelete(rootTag, tableName, indent);
            }
            if (templates.contains("select")) {
                generateSelect(rootTag, tableName, fields, indent);
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
                    .append("<result column=\"").append(NameUtil.camelToSnake(field.name()))
                    .append("\" property=\"").append(field.name())
                    .append("\" jdbcType=\"").append(field.jdbcType()).append("\"/>\n");
        }
        sb.append(indent).append("</resultMap>\n");
        rootTag.getValue().setText(rootTag.getValue().getText() + sb);
    }

    private void generateInsert(@NotNull XmlTag rootTag, @NotNull String tableName,
            @NotNull List<FieldInfo> fields, @NotNull String indent) {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            FieldInfo field = fields.get(i);
            if (i > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(NameUtil.camelToSnake(field.name()));
            values.append("#{").append(field.name()).append(",jdbcType=").append(field.jdbcType()).append("}");
        }

        String sql = "\n" + indent + "<insert id=\"insert\">\n" +
                indent + indent + "INSERT INTO ${tableName}\n" +
                indent + indent + "(" + columns + ")\n" +
                indent + indent + "VALUES\n" +
                indent + indent + "(" + values + ")\n" +
                indent + "</insert>\n";
        rootTag.getValue().setText(rootTag.getValue().getText() + sql);
    }

    private void generateUpdate(@NotNull XmlTag rootTag, @NotNull String tableName,
            @NotNull List<FieldInfo> fields, @NotNull String indent) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append(indent).append("<update id=\"update\">\n");
        sb.append(indent).append(indent).append("UPDATE ${tableName}\n");
        sb.append(indent).append(indent).append("<set>\n");
        for (FieldInfo field : fields) {
            sb.append(indent).append(indent).append(indent)
                    .append("<if test=\"").append(field.name()).append(" != null\">")
                    .append(NameUtil.camelToSnake(field.name())).append(" = #{")
                    .append(field.name()).append(",jdbcType=").append(field.jdbcType())
                    .append("},</if>\n");
        }
        sb.append(indent).append(indent).append("</set>\n");
        sb.append(indent).append(indent).append("WHERE id = #{id}\n");
        sb.append(indent).append("</update>\n");
        rootTag.getValue().setText(rootTag.getValue().getText() + sb);
    }

    private void generateDelete(@NotNull XmlTag rootTag, @NotNull String tableName, @NotNull String indent) {
        String sql = "\n" + indent + "<delete id=\"delete\">\n" +
                indent + indent + "DELETE FROM ${tableName}\n" +
                indent + indent + "WHERE id = #{id}\n" +
                indent + "</delete>\n";
        rootTag.getValue().setText(rootTag.getValue().getText() + sql);
    }

    private void generateSelect(@NotNull XmlTag rootTag, @NotNull String tableName,
            @NotNull List<FieldInfo> fields, @NotNull String indent) {
        StringBuilder columns = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                columns.append(", ");
            }
            columns.append(NameUtil.camelToSnake(fields.get(i).name()));
        }

        String sql = "\n" + indent + "<select id=\"selectById\" resultMap=\"BaseResultMap\">\n" +
                indent + indent + "SELECT " + columns + "\n" +
                indent + indent + "FROM ${tableName}\n" +
                indent + indent + "WHERE id = #{id}\n" +
                indent + "</select>\n";
        rootTag.getValue().setText(rootTag.getValue().getText() + sql);
    }
}
