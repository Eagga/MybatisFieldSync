package com.eagga.mybatisfieldsync.ui;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/**
 * 批量同步入口：先选实体，再逐项进入现有预览/执行流程。
 */
public class BatchSyncWizardDialog extends DialogWrapper {
    private final BatchSyncEntityTableModel tableModel = new BatchSyncEntityTableModel(
            MyBatisFieldSyncBundle.message("table.column.select"),
            MyBatisFieldSyncBundle.message("dialog.batch.entity"));

    public BatchSyncWizardDialog(Project project, List<PsiClass> entities, @Nullable PsiClass preselectedClass) {
        super(project);
        setTitle(MyBatisFieldSyncBundle.message("dialog.batch.title"));
        setOKButtonText(MyBatisFieldSyncBundle.message("dialog.batch.start"));
        setResizable(true);
        init();

        tableModel.setEntities(entities);
        if (preselectedClass != null) {
            tableModel.setSelectedEntities(List.of(preselectedClass));
        }
    }

    public List<PsiClass> getSelectedEntities() {
        return tableModel.getSelectedEntities();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.add(new JBLabel(MyBatisFieldSyncBundle.message("dialog.batch.instructions")), BorderLayout.NORTH);

        JBTable table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(64);
        table.getColumnModel().getColumn(1).setPreferredWidth(560);
        root.add(new JBScrollPane(table), BorderLayout.CENTER);
        root.add(createBottomPanel(), BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(820, 560));
        return root;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton selectAllButton = new JButton(new AbstractAction(MyBatisFieldSyncBundle.message("dialog.selectAll")) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                tableModel.selectAll();
            }
        });
        JButton selectNoneButton = new JButton(new AbstractAction(MyBatisFieldSyncBundle.message("dialog.selectNone")) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                tableModel.clearSelection();
            }
        });

        panel.add(selectAllButton);
        panel.add(selectNoneButton);
        return panel;
    }
}
