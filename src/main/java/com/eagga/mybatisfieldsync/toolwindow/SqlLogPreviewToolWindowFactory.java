package com.eagga.mybatisfieldsync.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class SqlLogPreviewToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        SqlLogPreviewPanel panel = new SqlLogPreviewPanel(project, toolWindow.getDisposable());
        Content content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
