package com.eagga.mybatisfieldsync.action;

import com.eagga.mybatisfieldsync.service.SyncHistoryService;
import com.eagga.mybatisfieldsync.ui.SyncHistoryDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ViewSyncHistoryAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        SyncHistoryService historyService = project.getService(SyncHistoryService.class);
        new SyncHistoryDialog(project, historyService).show();
    }
}
