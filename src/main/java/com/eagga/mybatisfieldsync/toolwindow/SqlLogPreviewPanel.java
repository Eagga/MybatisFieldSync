package com.eagga.mybatisfieldsync.toolwindow;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.service.SqlLogPreviewService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;

final class SqlLogPreviewPanel {
    private final JPanel rootPanel;
    private final JCheckBox enableCheckBox;
    private final JButton clearButton;
    private final JBTextArea logTextArea;
    private final JBLabel statusLabel;
    private final SqlLogPreviewService previewService;

    SqlLogPreviewPanel(@NotNull Project project, @NotNull Disposable parentDisposable) {
        this.previewService = project.getService(SqlLogPreviewService.class);
        this.rootPanel = new JPanel(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(6)));
        this.enableCheckBox = new JCheckBox(MyBatisFieldSyncBundle.message("sql.preview.enable"));
        this.clearButton = new JButton(MyBatisFieldSyncBundle.message("sql.preview.clear"));
        this.statusLabel = new JBLabel();

        this.enableCheckBox.setSelected(previewService.isEnabled());
        updateStatus(previewService.isEnabled());

        topPanel.add(enableCheckBox);
        topPanel.add(clearButton);
        topPanel.add(statusLabel);

        this.logTextArea = new JBTextArea();
        this.logTextArea.setEditable(false);
        this.logTextArea.setLineWrap(false);
        this.logTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, this.logTextArea.getFont().getSize()));
        this.logTextArea.setText(previewService.getSnapshot());

        JBScrollPane scrollPane = new JBScrollPane(logTextArea);
        scrollPane.setBorder(JBUI.Borders.empty());

        JBLabel hintLabel = new JBLabel(MyBatisFieldSyncBundle.message("sql.preview.hint"));
        hintLabel.setBorder(JBUI.Borders.emptyLeft(4));

        rootPanel.setBorder(JBUI.Borders.empty(6));
        rootPanel.add(topPanel, BorderLayout.NORTH);
        rootPanel.add(scrollPane, BorderLayout.CENTER);
        rootPanel.add(hintLabel, BorderLayout.SOUTH);

        enableCheckBox.addActionListener(e -> previewService.setEnabled(enableCheckBox.isSelected()));
        clearButton.addActionListener(e -> previewService.clear());

        previewService.addListener(new SqlLogPreviewService.Listener() {
            @Override
            public void onAppended(@NotNull String text) {
                logTextArea.append(text);
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            }

            @Override
            public void onCleared() {
                logTextArea.setText("");
            }

            @Override
            public void onEnabledChanged(boolean enabled) {
                enableCheckBox.setSelected(enabled);
                updateStatus(enabled);
            }
        }, parentDisposable);
    }

    JComponent getComponent() {
        return rootPanel;
    }

    private void updateStatus(boolean enabled) {
        statusLabel.setText(enabled
                ? MyBatisFieldSyncBundle.message("sql.preview.status.on")
                : MyBatisFieldSyncBundle.message("sql.preview.status.off"));
    }
}
