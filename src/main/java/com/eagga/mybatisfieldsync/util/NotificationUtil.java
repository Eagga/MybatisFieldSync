package com.eagga.mybatisfieldsync.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * IDEA 通知组封装，统一插件提示风格。
 */
public final class NotificationUtil {
    private static final String GROUP_ID = "MyBatis Field Sync";

    private NotificationUtil() {
    }

    public static void info(@NotNull Project project, @NotNull String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(content, NotificationType.INFORMATION)
                .notify(project);
    }

    public static void error(@NotNull Project project, @NotNull String content) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(content, NotificationType.ERROR)
                .notify(project);
    }
}
