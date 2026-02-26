package com.eagga.mybatisfieldsync.i18n;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * i18n 资源访问入口。
 */
public final class MyBatisFieldSyncBundle {
    @NonNls
    private static final String BUNDLE = "messages.MyBatisFieldSyncBundle";

    private static final DynamicBundle INSTANCE = new DynamicBundle(MyBatisFieldSyncBundle.class, BUNDLE);

    private MyBatisFieldSyncBundle() {
    }

    /**
     * 根据 key 解析本地化文案，可带占位参数。
     */
    public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key,
                                                Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}
