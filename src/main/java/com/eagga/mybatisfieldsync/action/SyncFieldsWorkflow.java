package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.EntitySyncResult;
import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.model.StaleFieldInfo;
import com.eagga.mybatisfieldsync.model.StatementInfo;
import com.eagga.mybatisfieldsync.model.SyncException;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.eagga.mybatisfieldsync.ui.FieldSelectionDialog;
import com.eagga.mybatisfieldsync.ui.PreviewDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 复用单实体"选择 -> 预览 -> 执行"同步链路。
 */
public final class SyncFieldsWorkflow {
    private SyncFieldsWorkflow() {
    }

    public static @NotNull EntitySyncResult run(@NotNull Project project,
            @NotNull FieldSyncService service,
            @NotNull PsiClass targetClass) {
        String entityName = resolveEntityName(targetClass);
        String shortName = targetClass.getName() == null ? entityName : targetClass.getName();

        List<XmlFile> xmlFiles = service.findCandidateXmlFiles(targetClass);
        if (xmlFiles.isEmpty()) {
            return EntitySyncResult.failed(entityName, MyBatisFieldSyncBundle.message("notify.noXml", shortName));
        }

        FieldSelectionDialog dialog = new FieldSelectionDialog(project, service, targetClass, xmlFiles);
        dialog.setTitle(MyBatisFieldSyncBundle.message("dialog.title.withEntity", shortName));
        if (!dialog.showAndGet()) {
            return EntitySyncResult.skipped(entityName, MyBatisFieldSyncBundle.message("batch.item.skipped.selection"));
        }

        List<FieldInfo> selectedFields = dialog.getSelectedFields();
        if (selectedFields.isEmpty()) {
            return EntitySyncResult.failed(entityName, MyBatisFieldSyncBundle.message("notify.noField"));
        }
        List<FieldInfo> allFieldsInOrder = dialog.getAllFieldsInOrder();
        boolean cleanStale = dialog.isCleanStaleFields();

        XmlFile xmlFile = dialog.getSelectedXmlFile();
        List<StatementInfo> statements = dialog.getSelectedStatements();
        if (xmlFile == null || statements.isEmpty()) {
            return EntitySyncResult.failed(entityName, MyBatisFieldSyncBundle.message("notify.noStatement"));
        }

        // Detect stale fields if cleanup is requested
        List<StaleFieldInfo> allStaleFields = new ArrayList<>();
        if (cleanStale) {
            for (StatementInfo statement : statements) {
                allStaleFields.addAll(service.detectStaleFields(statement, allFieldsInOrder));
            }
        }

        XmlFile previewFile = (XmlFile) PsiFileFactory.getInstance(project)
                .createFileFromText(xmlFile.getName(), xmlFile.getFileType(), xmlFile.getText());

        List<String> previewFailures = new ArrayList<>();
        ApplicationManager.getApplication().runWriteAction(() -> {
            // Apply stale field removal to preview
            if (!allStaleFields.isEmpty()) {
                for (StatementInfo statement : statements) {
                    XmlTag previewTag = findEquivalentTag(previewFile, statement.tag());
                    if (previewTag == null) {
                        continue;
                    }
                    List<StaleFieldInfo> staleForStatement = allStaleFields.stream()
                            .filter(s -> s.statementId().equals(statement.id()))
                            .toList();
                    if (!staleForStatement.isEmpty()) {
                        StatementInfo previewStatement = new StatementInfo(statement.id(), statement.tagName(), previewTag);
                        service.removeStaleFieldsDirect(previewStatement, staleForStatement);
                    }
                }
            }

            // Apply field sync to preview
            for (StatementInfo statement : statements) {
                try {
                    XmlTag previewTag = findEquivalentTag(previewFile, statement.tag());
                    if (previewTag == null) {
                        previewFailures.add(statement.id());
                        continue;
                    }
                    StatementInfo previewStatement = new StatementInfo(statement.id(), statement.tagName(), previewTag);
                    service.syncInWriteCommand(previewFile,
                            previewStatement,
                            selectedFields,
                            allFieldsInOrder,
                            shortName,
                            false);
                } catch (Exception ex) {
                    previewFailures.add(statement.id() + ": " + ex.getMessage());
                }
            }
        });

        if (!previewFailures.isEmpty() && previewFailures.size() == statements.size()) {
            return EntitySyncResult.failed(entityName,
                    MyBatisFieldSyncBundle.message("notify.preview.failed", String.join("; ", previewFailures)));
        }

        // Build preview text with stale field summary
        String previewText = buildPreviewText(previewFile.getText(), allStaleFields);

        PreviewDialog previewDialog = new PreviewDialog(project,
                MyBatisFieldSyncBundle.message("dialog.preview.title", shortName),
                MyBatisFieldSyncBundle.message("dialog.preview.execute"),
                MyBatisFieldSyncBundle.message("dialog.preview.skip"),
                previewText);
        if (!previewDialog.showAndGet()) {
            return EntitySyncResult.skipped(entityName, MyBatisFieldSyncBundle.message("batch.item.skipped.preview"));
        }

        // Execute: first remove stale fields, then sync new fields
        List<String> failedStatements = new ArrayList<>();
        List<String> successStatementIds = new ArrayList<>();

        if (!allStaleFields.isEmpty()) {
            for (StatementInfo statement : statements) {
                List<StaleFieldInfo> staleForStatement = allStaleFields.stream()
                        .filter(s -> s.statementId().equals(statement.id()))
                        .toList();
                if (!staleForStatement.isEmpty()) {
                    try {
                        service.removeStaleFieldsInWriteCommand(xmlFile, statement, staleForStatement);
                    } catch (Exception ex) {
                        failedStatements.add(statement.id() + " (cleanup): " + ex.getMessage());
                    }
                }
            }
        }

        for (StatementInfo statement : statements) {
            try {
                service.syncInWriteCommand(xmlFile, statement, selectedFields, allFieldsInOrder, shortName);
                successStatementIds.add(statement.id());
            } catch (SyncException ex) {
                failedStatements.add(statement.id() + ": " + ex.getMessage());
            }
        }

        String xmlPath = xmlFile.getVirtualFile() == null ? xmlFile.getName() : xmlFile.getVirtualFile().getPath();
        if (!successStatementIds.isEmpty() && failedStatements.isEmpty()) {
            return EntitySyncResult.success(entityName, xmlPath, selectedFields.size(), successStatementIds);
        }
        if (!successStatementIds.isEmpty()) {
            return EntitySyncResult.partial(entityName,
                    xmlPath,
                    selectedFields.size(),
                    successStatementIds,
                    failedStatements);
        }
        return EntitySyncResult.failed(entityName, String.join("; ", failedStatements));
    }

    private static @NotNull String buildPreviewText(@NotNull String xmlContent,
            @NotNull List<StaleFieldInfo> staleFields) {
        if (staleFields.isEmpty()) {
            return xmlContent;
        }
        StringBuilder header = new StringBuilder();
        header.append("// ").append(MyBatisFieldSyncBundle.message("preview.stale.header",
                staleFields.size())).append("\n");
        for (StaleFieldInfo stale : staleFields) {
            header.append("//   - ").append(stale.toString()).append("\n");
        }
        header.append("// ").append(MyBatisFieldSyncBundle.message("preview.stale.separator")).append("\n\n");
        return header + xmlContent;
    }

    private static @NotNull String resolveEntityName(@NotNull PsiClass targetClass) {
        if (targetClass.getQualifiedName() != null) {
            return targetClass.getQualifiedName();
        }
        if (targetClass.getName() != null) {
            return targetClass.getName();
        }
        return "<anonymous>";
    }

    private static XmlTag findEquivalentTag(@NotNull XmlFile file, @NotNull XmlTag original) {
        XmlTag rootTag = file.getRootTag();
        if (rootTag == null) {
            return null;
        }
        for (XmlTag child : rootTag.getSubTags()) {
            if (!original.getName().equals(child.getName())) {
                continue;
            }
            String originalId = original.getAttributeValue("id");
            if (originalId != null && originalId.equals(child.getAttributeValue("id"))) {
                return child;
            }
        }
        return null;
    }
}
