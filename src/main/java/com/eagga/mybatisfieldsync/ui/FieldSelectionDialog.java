package com.eagga.mybatisfieldsync.ui;

import com.eagga.mybatisfieldsync.i18n.MyBatisFieldSyncBundle;
import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.eagga.mybatisfieldsync.model.StatementInfo;
import com.eagga.mybatisfieldsync.service.FieldSyncService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/**
 * 字段与目标 Mapper Statement 的选择对话框。
 */
public class FieldSelectionDialog extends DialogWrapper {
    private final FieldSyncService fieldSyncService;
    private final PsiClass psiClass;
    private final List<XmlFile> xmlFiles;

    private final JComboBox<XmlFile> xmlFileComboBox = new JComboBox<>();
    private final DefaultListModel<StatementInfo> statementListModel = new DefaultListModel<>();
    private final JBList<StatementInfo> statementList = new JBList<>(statementListModel);
    private final JBCheckBox includeInheritedBox = new JBCheckBox(MyBatisFieldSyncBundle.message("dialog.includeInherited"), true);
    private final FieldSelectionTableModel tableModel = new FieldSelectionTableModel(
            MyBatisFieldSyncBundle.message("table.column.select"),
            MyBatisFieldSyncBundle.message("table.column.field"),
            MyBatisFieldSyncBundle.message("table.column.type")
    );

    public FieldSelectionDialog(Project project,
                                FieldSyncService fieldSyncService,
                                PsiClass psiClass,
                                List<XmlFile> xmlFiles) {
        super(project);
        this.fieldSyncService = fieldSyncService;
        this.psiClass = psiClass;
        this.xmlFiles = xmlFiles;

        setTitle(MyBatisFieldSyncBundle.message("dialog.title"));
        setResizable(true);
        init();

        loadXmlFiles();
        reloadFields();
    }

    public List<FieldInfo> getSelectedFields() {
        return tableModel.getSelectedFields();
    }

    public List<FieldInfo> getAllFieldsInOrder() {
        return tableModel.getAllFields();
    }

    public XmlFile getSelectedXmlFile() {
        return (XmlFile) xmlFileComboBox.getSelectedItem();
    }

    public List<StatementInfo> getSelectedStatements() {
        return statementList.getSelectedValuesList();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.add(createTopPanel(), BorderLayout.NORTH);

        JBTable table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(64);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);

        root.add(new JBScrollPane(table), BorderLayout.CENTER);
        root.add(createBottomPanel(), BorderLayout.SOUTH);
        root.setPreferredSize(new Dimension(820, 620));
        return root;
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JBLabel(MyBatisFieldSyncBundle.message("dialog.xmlFile")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(xmlFileComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JBLabel(MyBatisFieldSyncBundle.message("dialog.statement")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        JBScrollPane statementScrollPane = new JBScrollPane(statementList);
        statementScrollPane.setPreferredSize(new Dimension(480, 120));
        panel.add(statementScrollPane, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(includeInheritedBox, gbc);

        // Statement 列表依赖当前选择的 XML。
        xmlFileComboBox.addActionListener(e -> reloadStatements());
        includeInheritedBox.addActionListener(e -> reloadFields());

        statementList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        statementList.setCellRenderer(new SimpleStatementRenderer());

        return panel;
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

    private void loadXmlFiles() {
        DefaultComboBoxModel<XmlFile> model = new DefaultComboBoxModel<>();
        for (XmlFile xmlFile : xmlFiles) {
            model.addElement(xmlFile);
        }
        xmlFileComboBox.setModel(model);
        if (!xmlFiles.isEmpty()) {
            xmlFileComboBox.setSelectedIndex(0);
        }
        xmlFileComboBox.setRenderer(new SimpleXmlFileRenderer());
        reloadStatements();
    }

    /**
     * 根据当前 XML 重新加载可选 Statement ID。
     */
    private void reloadStatements() {
        statementListModel.clear();
        XmlFile selectedXml = getSelectedXmlFile();
        if (selectedXml != null) {
            List<StatementInfo> statements = fieldSyncService.collectStatements(selectedXml);
            for (StatementInfo statement : statements) {
                statementListModel.addElement(statement);
            }
        }

        if (!statementListModel.isEmpty()) {
            statementList.setSelectedIndex(0);
        }
    }

    /**
     * 继承字段开关变化后，重新加载字段表格。
     */
    private void reloadFields() {
        List<FieldInfo> fields = fieldSyncService.collectFields(psiClass, includeInheritedBox.isSelected());
        tableModel.setFields(fields);
        tableModel.selectAll();
    }
}
