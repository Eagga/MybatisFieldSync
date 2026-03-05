import re

with open("src/main/java/com/eagga/mybatisfieldsync/action/SyncFieldsAction.java", "r") as f:
    text = f.read()

# Add imports
imports = """import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlTag;
import com.eagga.mybatisfieldsync.ui.PreviewDialog;
import com.intellij.openapi.application.ApplicationManager;
import java.util.Locale;"""
text = text.replace("import java.util.List;", "import java.util.List;\n" + imports)

# Find where dialog returns
old_logic = """        List<String> failedStatements = new ArrayList<>();
        List<String> successStatementIds = new ArrayList<>();
        int successCount = 0;
        for (StatementInfo statement : statements) {
            try {
                service.syncInWriteCommand(xmlFile, statement, selectedFields, allFieldsInOrder);
                successCount++;
                successStatementIds.add(statement.id());
            } catch (SyncException ex) {
                failedStatements.add(statement.id() + ": " + ex.getMessage());
            }
        }"""

new_logic = """        // Preview feature
        XmlFile copyFile = (XmlFile) PsiFileFactory.getInstance(project).createFileFromText(
                xmlFile.getName(), xmlFile.getFileType(), xmlFile.getText());
        
        List<String> preFailed = new ArrayList<>();
        ApplicationManager.getApplication().runWriteAction(() -> {
            for (StatementInfo stmt : statements) {
                try {
                    XmlTag mockTag = findEquivalentTag(copyFile, stmt.tag());
                    if (mockTag != null) {
                        StatementInfo mockStmt = new StatementInfo(stmt.id(), stmt.type(), mockTag);
                        service.syncInWriteCommand(copyFile, mockStmt, selectedFields, allFieldsInOrder);
                    }
                } catch (Exception ex) {
                    preFailed.add(stmt.id() + ": " + ex.getMessage());
                }
            }
        });

        if (!preFailed.isEmpty() && preFailed.size() == statements.size()) {
            NotificationUtil.error(project, MyBatisFieldSyncBundle.message("notify.sync.partialFailed", String.join("; ", preFailed)));
            return;
        }

        PreviewDialog previewDialog = new PreviewDialog(project, copyFile.getText());
        if (!previewDialog.showAndGet()) {
            return;
        }

        // Execute actual
        List<String> failedStatements = new ArrayList<>();
        List<String> successStatementIds = new ArrayList<>();
        int successCount = 0;
        for (StatementInfo statement : statements) {
            try {
                service.syncInWriteCommand(xmlFile, statement, selectedFields, allFieldsInOrder);
                successCount++;
                successStatementIds.add(statement.id());
            } catch (SyncException ex) {
                failedStatements.add(statement.id() + ": " + ex.getMessage());
            }
        }"""
text = text.replace(old_logic, new_logic)

helper = """
    private XmlTag findEquivalentTag(XmlFile file, XmlTag original) {
        if (file.getRootTag() == null) return null;
        for (XmlTag child : file.getRootTag().getSubTags()) {
            if (original.getName().equals(child.getName()) &&
                original.getAttributeValue("id") != null &&
                original.getAttributeValue("id").equals(child.getAttributeValue("id"))) {
                return child;
            }
        }
        return null;
    }
}"""
text = text.replace("\n}", helper)

with open("src/main/java/com/eagga/mybatisfieldsync/action/SyncFieldsAction.java", "w") as f:
    f.write(text)
