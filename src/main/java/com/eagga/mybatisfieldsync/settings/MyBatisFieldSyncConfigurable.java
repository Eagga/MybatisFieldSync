package com.eagga.mybatisfieldsync.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MyBatisFieldSyncConfigurable implements Configurable {
    private final Project project;
    private JComboBox<String> indentComboBox;
    private JComboBox<String> lineBreakComboBox;
    private JComboBox<String> commaComboBox;
    private JTextArea jdbcMappingTextArea;
    private JTextArea typeHandlerTextArea;

    public MyBatisFieldSyncConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public String getDisplayName() {
        return "MyBatis Field Sync";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel formatPanel = new JPanel(new GridLayout(3, 2, 8, 8));
        formatPanel.setBorder(BorderFactory.createTitledBorder("XML Formatting Strategy"));
        indentComboBox = new JComboBox<>(new String[]{"AUTO", "SPACE_4", "SPACE_2", "TAB"});
        lineBreakComboBox = new JComboBox<>(new String[]{"AUTO", "MULTI_LINE", "SINGLE_LINE"});
        commaComboBox = new JComboBox<>(new String[]{"AUTO", "TRAILING", "LEADING"});
        formatPanel.add(new JLabel("Indent:"));
        formatPanel.add(indentComboBox);
        formatPanel.add(new JLabel("Line break:"));
        formatPanel.add(lineBreakComboBox);
        formatPanel.add(new JLabel("Comma style:"));
        formatPanel.add(commaComboBox);

        jdbcMappingTextArea = new JTextArea(6, 60);
        typeHandlerTextArea = new JTextArea(6, 60);

        JPanel jdbcPanel = new JPanel(new BorderLayout(0, 6));
        jdbcPanel.setBorder(BorderFactory.createTitledBorder("Custom JdbcType Mapping"));
        jdbcPanel.add(new JLabel("Format: javaType=jdbcType"), BorderLayout.NORTH);
        jdbcPanel.add(new JScrollPane(jdbcMappingTextArea), BorderLayout.CENTER);

        JPanel handlerPanel = new JPanel(new BorderLayout(0, 6));
        handlerPanel.setBorder(BorderFactory.createTitledBorder("Custom TypeHandler Mapping"));
        handlerPanel.add(new JLabel("Format: javaType=typeHandlerClass[,jdbcType=XXX]"), BorderLayout.NORTH);
        handlerPanel.add(new JScrollPane(typeHandlerTextArea), BorderLayout.CENTER);

        panel.add(formatPanel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(jdbcPanel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(handlerPanel);
        return panel;
    }

    @Override
    public boolean isModified() {
        MyBatisFieldSyncSettings.State state = MyBatisFieldSyncSettings.getInstance(project).getState();
        if (state == null) {
            return false;
        }
        return !jdbcMappingTextArea.getText().equals(state.customMappingConfig)
                || !typeHandlerTextArea.getText().equals(state.typeHandlerMappingConfig)
                || !String.valueOf(indentComboBox.getSelectedItem()).equals(state.xmlIndentStyle)
                || !String.valueOf(lineBreakComboBox.getSelectedItem()).equals(state.xmlLineBreakStyle)
                || !String.valueOf(commaComboBox.getSelectedItem()).equals(state.xmlCommaStyle);
    }

    @Override
    public void apply() {
        MyBatisFieldSyncSettings.State state = MyBatisFieldSyncSettings.getInstance(project).getState();
        if (state != null) {
            state.customMappingConfig = jdbcMappingTextArea.getText();
            state.typeHandlerMappingConfig = typeHandlerTextArea.getText();
            state.xmlIndentStyle = String.valueOf(indentComboBox.getSelectedItem());
            state.xmlLineBreakStyle = String.valueOf(lineBreakComboBox.getSelectedItem());
            state.xmlCommaStyle = String.valueOf(commaComboBox.getSelectedItem());
        }
    }

    @Override
    public void reset() {
        MyBatisFieldSyncSettings.State state = MyBatisFieldSyncSettings.getInstance(project).getState();
        if (state != null) {
            jdbcMappingTextArea.setText(state.customMappingConfig);
            typeHandlerTextArea.setText(state.typeHandlerMappingConfig);
            indentComboBox.setSelectedItem(state.xmlIndentStyle);
            lineBreakComboBox.setSelectedItem(state.xmlLineBreakStyle);
            commaComboBox.setSelectedItem(state.xmlCommaStyle);
        }
    }
}
