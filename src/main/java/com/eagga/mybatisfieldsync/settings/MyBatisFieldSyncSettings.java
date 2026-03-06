package com.eagga.mybatisfieldsync.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "MyBatisFieldSyncSettings", storages = @Storage("mybatis-field-sync.xml"))
public final class MyBatisFieldSyncSettings implements PersistentStateComponent<MyBatisFieldSyncSettings.State> {
    private State myState = new State();

    public static class State {
        public String customMappingConfig = "java.util.Date=TIMESTAMP\njava.math.BigDecimal=DECIMAL";
    }

    public static MyBatisFieldSyncSettings getInstance(Project project) {
        return project.getService(MyBatisFieldSyncSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }
}
