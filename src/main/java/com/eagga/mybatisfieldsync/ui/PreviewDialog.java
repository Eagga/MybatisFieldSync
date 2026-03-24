package com.eagga.mybatisfieldsync.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.awt.Dimension;

public class PreviewDialog extends DialogWrapper {
    private final String previewText;
    private final String dialogTitle;
    private final String okText;
    private final String cancelText;

    public PreviewDialog(Project project, String previewText) {
        this(project, "Preview Synchronization", "Execute", "Cancel", previewText);
    }

    public PreviewDialog(Project project, String dialogTitle, String okText, String cancelText, String previewText) {
        super(project);
        this.previewText = previewText;
        this.dialogTitle = dialogTitle;
        this.okText = okText;
        this.cancelText = cancelText;
        setTitle(dialogTitle);
        setOKButtonText(okText);
        setCancelButtonText(cancelText);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JTextArea textArea = new JTextArea(previewText);
        textArea.setEditable(false);
        textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(800, 600));
        return scrollPane;
    }
}
