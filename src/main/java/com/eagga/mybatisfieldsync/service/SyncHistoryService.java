package com.eagga.mybatisfieldsync.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.PROJECT)
@State(name = "MyBatisSyncHistory", storages = @Storage("mybatis-sync-history.xml"))
public final class SyncHistoryService implements PersistentStateComponent<SyncHistoryService.State> {
    private State state = new State();
    private static final int MAX_HISTORY = 50;

    public static class HistoryEntry {
        public String timestamp;
        public String entityClass;
        public String xmlFile;
        public String statementId;
        public List<String> fields = new ArrayList<>();
    }

    public static class State {
        public List<HistoryEntry> entries = new ArrayList<>();
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public void addEntry(@NotNull String entityClass, @NotNull String xmlFile,
                        @NotNull String statementId, @NotNull List<String> fields) {
        HistoryEntry entry = new HistoryEntry();
        entry.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        entry.entityClass = entityClass;
        entry.xmlFile = xmlFile;
        entry.statementId = statementId;
        entry.fields = new ArrayList<>(fields);

        state.entries.add(0, entry);
        if (state.entries.size() > MAX_HISTORY) {
            state.entries.remove(state.entries.size() - 1);
        }
    }

    public List<HistoryEntry> getHistory() {
        return new ArrayList<>(state.entries);
    }

    public void clearHistory() {
        state.entries.clear();
    }
}
