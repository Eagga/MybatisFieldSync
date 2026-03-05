package com.eagga.mybatisfieldsync.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.eagga.mybatisfieldsync.service.SyncHistoryService;
import org.jetbrains.annotations.NotNull;

public class ViewSyncHistoryAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        SyncHistoryService service = project.getService(SyncHistoryService.class);
        String history = service.getFormattedHistory();

        Messages.showMessageDialog(project, history, "Sync History", Messages.getInformationIcon());
    }
}
