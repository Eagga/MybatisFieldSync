package com.eagga.mybatisfieldsync.ui;

import com.eagga.mybatisfieldsync.service.SyncHistoryService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class SyncHistoryDialog extends DialogWrapper {
    private final SyncHistoryService historyService;

    public SyncHistoryDialog(Project project, SyncHistoryService historyService) {
        super(project);
        this.historyService = historyService;
        setTitle("MyBatis Sync History");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        String[] columns = {"Time", "Entity", "XML File", "Statement", "Fields"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);

        for (SyncHistoryService.HistoryEntry entry : historyService.getHistory()) {
            model.addRow(new Object[]{
                    entry.timestamp,
                    entry.entityClass,
                    entry.xmlFile,
                    entry.statementId,
                    String.join(", ", entry.fields)
            });
        }

        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JBScrollPane scrollPane = new JBScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(800, 400));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton clearButton = new JButton("Clear History");
        clearButton.addActionListener(e -> {
            historyService.clearHistory();
            model.setRowCount(0);
        });
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }
}
