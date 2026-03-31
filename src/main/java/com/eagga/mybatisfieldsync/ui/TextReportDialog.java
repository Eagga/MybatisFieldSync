package com.eagga.mybatisfieldsync.ui;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.awt.Dimension;

/**
 * 只读文本报告对话框。
 */
public class TextReportDialog extends DialogWrapper {
    private final String content;

    public TextReportDialog(Project project, String title, String content) {
        super(project);
        this.content = content;
        setTitle(title);
        setOKButtonText(MyBatisFieldSyncBundle.message("dialog.report.close"));
        init();
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(860, 620));
        return scrollPane;
    }
}
