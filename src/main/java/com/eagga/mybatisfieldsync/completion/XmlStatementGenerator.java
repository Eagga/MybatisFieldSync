package com.eagga.mybatisfieldsync.completion;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.util.NotificationUtil;
import java.util.List;

public class XmlStatementGenerator {
    
    public static void generateXmlStatement(Project project, PsiClass entityClass, String methodName, 
                                           MethodNameParser.ParseResult parseResult) {
        FieldSyncService service = project.getService(FieldSyncService.class);
        List<XmlFile> xmlFiles = service.findCandidateXmlFiles(entityClass);
        
        if (xmlFiles.isEmpty()) {
            NotificationUtil.error(project, "No XML file found for " + entityClass.getName());
            return;
        }
        
        XmlFile xmlFile = xmlFiles.get(0);
        String sql = SqlGenerator.generateSql(parseResult, getTableName(entityClass), List.of());
        
        WriteCommandAction.runWriteCommandAction(project, () -> {
            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) return;

            XmlTag existing = findStatementById(rootTag, methodName);
            if (existing != null) {
                applyStatementAttributes(existing, parseResult, methodName);
                existing.getValue().setText(sql);
            } else {
                XmlTag statementTag = createStatementTag(rootTag, methodName, parseResult, sql);
                rootTag.addSubTag(statementTag, false);
            }
        });
        
        NotificationUtil.info(project, "Generated XML statement: " + methodName);
    }
    
    private static XmlTag createStatementTag(XmlTag rootTag, String methodName,
                                            MethodNameParser.ParseResult parseResult, String sql) {
        String tagName = getTagName(parseResult.prefix);
        XmlTag tag = rootTag.createChildTag(tagName, null, sql, false);
        applyStatementAttributes(tag, parseResult, methodName);
        return tag;
    }

    private static void applyStatementAttributes(XmlTag tag, MethodNameParser.ParseResult parseResult, String methodName) {
        tag.setAttribute("id", methodName);
        tag.setAttribute("resultMap", null);
        tag.setAttribute("resultType", null);

        if ("findBy".equals(parseResult.prefix)) {
            tag.setAttribute("resultMap", "BaseResultMap");
        } else if ("countBy".equals(parseResult.prefix)) {
            tag.setAttribute("resultType", "java.lang.Long");
        } else if ("existsBy".equals(parseResult.prefix)) {
            tag.setAttribute("resultType", "java.lang.Boolean");
        }
    }

    private static XmlTag findStatementById(XmlTag rootTag, String methodName) {
        for (XmlTag tag : rootTag.getSubTags()) {
            if (methodName.equals(tag.getAttributeValue("id"))) {
                return tag;
            }
        }
        return null;
    }
    
    private static String getTagName(String prefix) {
        return switch (prefix) {
            case "findBy", "countBy", "existsBy" -> "select";
            case "deleteBy" -> "delete";
            default -> "select";
        };
    }
    
    private static String getTableName(PsiClass entityClass) {
        return com.eagga.mybatisfieldsync.util.NameUtil.camelToSnake(entityClass.getName());
    }
}
