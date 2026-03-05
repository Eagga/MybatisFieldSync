package com.eagga.mybatisfieldsync.ui;

import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CrudTemplateDialog extends DialogWrapper {
    private final PsiClass entityClass;
    private final XmlFile xmlFile;
    private final List<FieldInfo> fields;
    private JCheckBox resultMapCheckBox;
    private JCheckBox insertCheckBox;
    private JCheckBox updateCheckBox;
    private JCheckBox deleteCheckBox;
    private JCheckBox selectCheckBox;

    public CrudTemplateDialog(Project project, PsiClass entityClass, XmlFile xmlFile, List<FieldInfo> fields) {
        super(project);
        this.entityClass = entityClass;
        this.xmlFile = xmlFile;
        this.fields = fields;
        setTitle("Generate CRUD Templates");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 1, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Select templates to generate for " + entityClass.getName() + ":");
        panel.add(label);

        resultMapCheckBox = new JCheckBox("ResultMap (BaseResultMap)", true);
        insertCheckBox = new JCheckBox("Insert Statement", true);
        updateCheckBox = new JCheckBox("Update Statement", true);
        deleteCheckBox = new JCheckBox("Delete Statement", true);
        selectCheckBox = new JCheckBox("Select Statement", true);

        panel.add(resultMapCheckBox);
        panel.add(insertCheckBox);
        panel.add(updateCheckBox);
        panel.add(deleteCheckBox);
        panel.add(selectCheckBox);

        return panel;
    }

    public Set<String> getSelectedTemplates() {
        Set<String> templates = new HashSet<>();
        if (resultMapCheckBox.isSelected()) templates.add("resultMap");
        if (insertCheckBox.isSelected()) templates.add("insert");
        if (updateCheckBox.isSelected()) templates.add("update");
        if (deleteCheckBox.isSelected()) templates.add("delete");
        if (selectCheckBox.isSelected()) templates.add("select");
        return templates;
    }
}
