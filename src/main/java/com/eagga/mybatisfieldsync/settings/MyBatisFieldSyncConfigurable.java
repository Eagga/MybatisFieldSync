package com.eagga.mybatisfieldsync.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MyBatisFieldSyncConfigurable implements Configurable {
    private final Project project;
    private JTextArea configTextArea;

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
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        JLabel hintLabel = new JLabel("Custom JdbcType Mapping (format: javaType=jdbcType per line):");
        panel.add(hintLabel, BorderLayout.NORTH);

        configTextArea = new JTextArea();
        panel.add(new JScrollPane(configTextArea), BorderLayout.CENTER);

        return panel;
    }

    @Override
    public boolean isModified() {
        MyBatisFieldSyncSettings.State state = MyBatisFieldSyncSettings.getInstance(project).getState();
        if (state == null)
            return false;
        return !configTextArea.getText().equals(state.customMappingConfig);
    }

    @Override
    public void apply() {
        MyBatisFieldSyncSettings.State state = MyBatisFieldSyncSettings.getInstance(project).getState();
        if (state != null) {
            state.customMappingConfig = configTextArea.getText();
        }
    }

    @Override
    public void reset() {
        MyBatisFieldSyncSettings.State state = MyBatisFieldSyncSettings.getInstance(project).getState();
        if (state != null) {
            configTextArea.setText(state.customMappingConfig);
        }
    }
}
