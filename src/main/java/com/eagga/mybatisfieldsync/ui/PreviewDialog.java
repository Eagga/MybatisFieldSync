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

    public PreviewDialog(Project project, String previewText) {
        super(project);
        this.previewText = previewText;
        setTitle("Preview Synchronization");
        setOKButtonText("Execute");
        setCancelButtonText("Cancel");
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
