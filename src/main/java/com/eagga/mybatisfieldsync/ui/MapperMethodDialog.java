package com.eagga.mybatisfieldsync.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Mapper 方法生成对话框
 */
public class MapperMethodDialog extends DialogWrapper {
    private final PsiClass entityClass;
    private final PsiClass mapperInterface;
    private JCheckBox insertCheckBox;
    private JCheckBox updateCheckBox;
    private JCheckBox deleteCheckBox;
    private JCheckBox selectByIdCheckBox;
    private JCheckBox selectListCheckBox;

    public MapperMethodDialog(Project project, PsiClass entityClass, PsiClass mapperInterface) {
        super(project);
        this.entityClass = entityClass;
        this.mapperInterface = mapperInterface;
        setTitle("Generate Mapper Methods");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridLayout(7, 1, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("Generate methods in " + mapperInterface.getName() + ":");
        panel.add(label);

        JLabel entityLabel = new JLabel("Entity: " + entityClass.getName());
        entityLabel.setFont(entityLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(entityLabel);

        insertCheckBox = new JCheckBox("int insert(" + entityClass.getName() + " record)", true);
        updateCheckBox = new JCheckBox("int update(" + entityClass.getName() + " record)", true);
        deleteCheckBox = new JCheckBox("int delete(Long id)", true);
        selectByIdCheckBox = new JCheckBox(entityClass.getName() + " selectById(Long id)", true);
        selectListCheckBox = new JCheckBox("List<" + entityClass.getName() + "> selectList()", true);

        panel.add(insertCheckBox);
        panel.add(updateCheckBox);
        panel.add(deleteCheckBox);
        panel.add(selectByIdCheckBox);
        panel.add(selectListCheckBox);

        return panel;
    }

    public Set<String> getSelectedMethods() {
        Set<String> methods = new HashSet<>();
        if (insertCheckBox.isSelected()) methods.add("insert");
        if (updateCheckBox.isSelected()) methods.add("update");
        if (deleteCheckBox.isSelected()) methods.add("delete");
        if (selectByIdCheckBox.isSelected()) methods.add("selectById");
        if (selectListCheckBox.isSelected()) methods.add("selectList");
        return methods;
    }
}
