package com.eagga.mybatisfieldsync.completion;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class MapperMethodInsertHandler implements InsertHandler<LookupElement> {
    private final PsiClass entityClass;
    private final MethodNameParser.ParseResult parseResult;
    private static final Set<String> NO_PARAM_OPERATORS = Set.of("IsNull", "IsNotNull");
    private static final Set<String> COLLECTION_OPERATORS = Set.of("In", "NotIn");
    
    public MapperMethodInsertHandler(PsiClass entityClass, MethodNameParser.ParseResult parseResult) {
        this.entityClass = entityClass;
        this.parseResult = parseResult;
    }
    
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
        Editor editor = context.getEditor();
        Document document = editor.getDocument();
        String methodName = item.getLookupString();

        if (isInMethodDeclaration(context)) {
            int offset = context.getTailOffset();
            String methodSignature = generateMethodSignature();
            document.insertString(offset, methodSignature);
            editor.getCaretModel().moveToOffset(offset + methodSignature.length());
        } else {
            String declaration = buildFullMethodDeclaration(methodName);
            document.replaceString(context.getStartOffset(), context.getTailOffset(), declaration);
            editor.getCaretModel().moveToOffset(context.getStartOffset() + declaration.length());
        }
        
        PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
        
        XmlStatementGenerator.generateXmlStatement(context.getProject(), entityClass, 
                                                   methodName, parseResult);
    }
    
    private String generateMethodSignature() {
        StringBuilder sb = new StringBuilder("(");

        int paramCount = 0;
        for (int i = 0; i < parseResult.conditions.size(); i++) {
            MethodNameParser.ParseResult.Condition condition = parseResult.conditions.get(i);
            if (NO_PARAM_OPERATORS.contains(condition.operator)) {
                continue;
            }

            String fieldName = condition.field;
            PsiField field = entityClass.findFieldByName(fieldName, false);
            String type = field != null ? field.getType().getPresentableText() : "String";

            if ("Between".equals(condition.operator)) {
                paramCount = appendParam(sb, paramCount, type, fieldName + "Start");
                paramCount = appendParam(sb, paramCount, type, fieldName + "End");
                continue;
            }

            if (COLLECTION_OPERATORS.contains(condition.operator)) {
                String collectionType = "java.util.Collection<" + type + ">";
                paramCount = appendParam(sb, paramCount, collectionType, fieldName);
                continue;
            }

            paramCount = appendParam(sb, paramCount, type, fieldName);
        }

        sb.append(");");
        return sb.toString();
    }

    private int appendParam(StringBuilder sb, int paramCount, String type, String name) {
        if (paramCount > 0) {
            sb.append(", ");
        }
        sb.append("@org.apache.ibatis.annotations.Param(\"")
                .append(name)
                .append("\") ")
                .append(type)
                .append(" ")
                .append(name);
        return paramCount + 1;
    }

    private boolean isInMethodDeclaration(@NotNull InsertionContext context) {
        if (hasReturnTypePrefixOnSameLine(context)) {
            return true;
        }
        PsiElement elementAtStart = context.getFile().findElementAt(context.getStartOffset());
        PsiMethod method = PsiTreeUtil.getParentOfType(elementAtStart, PsiMethod.class);
        if (method == null) {
            PsiElement elementAtTail = context.getFile()
                    .findElementAt(Math.max(0, context.getTailOffset() - 1));
            method = PsiTreeUtil.getParentOfType(elementAtTail, PsiMethod.class);
        }
        return method != null;
    }

    private boolean hasReturnTypePrefixOnSameLine(@NotNull InsertionContext context) {
        CharSequence text = context.getDocument().getCharsSequence();
        int start = context.getStartOffset();
        int lineStart = start;
        while (lineStart > 0) {
            char c = text.charAt(lineStart - 1);
            if (c == '\n' || c == '\r') {
                break;
            }
            lineStart--;
        }
        String prefix = text.subSequence(lineStart, start).toString().trim();
        if (prefix.isEmpty()) {
            return false;
        }
        if (prefix.endsWith("return")) {
            return false;
        }
        if (prefix.contains("(") || prefix.contains("=") || prefix.contains(",")) {
            return false;
        }
        return prefix.matches(".*[\\w$>\\]]\\s+");
    }

    private String buildFullMethodDeclaration(String methodName) {
        String returnType = switch (parseResult.prefix) {
            case "countBy" -> "long";
            case "existsBy" -> "boolean";
            case "deleteBy" -> "int";
            default -> "java.util.List<" + entityClass.getName() + ">";
        };
        return returnType + " " + methodName + generateMethodSignature();
    }
}
