package com.eagga.mybatisfieldsync.service;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class SqlLogPreviewService {
    private static final String PREPARING_KEYWORD = "Preparing:";
    private static final String PARAMETERS_KEYWORD = "Parameters:";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\?");
    private static final Pattern PARAM_TYPE = Pattern.compile("^(.*)\\(([^()]+)\\)$");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_BUFFER_CHARS = 200_000;

    private final MessageBusConnection connection;
    private final Map<ProcessHandler, String> pendingSqlByProcess = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object bufferLock = new Object();
    private final StringBuilder buffer = new StringBuilder();
    private volatile boolean enabled;

    public SqlLogPreviewService(@NotNull Project project) {
        this.connection = project.getMessageBus().connect();
        this.connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId,
                                       @NotNull ExecutionEnvironment env,
                                       @NotNull ProcessHandler handler) {
                attachProcessListener(handler);
            }
        });
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        fireEnabledChanged(enabled);
    }

    public @NotNull String getSnapshot() {
        synchronized (bufferLock) {
            return buffer.toString();
        }
    }

    public void clear() {
        synchronized (bufferLock) {
            buffer.setLength(0);
        }
        fireCleared();
    }

    public void addListener(@NotNull Listener listener, @NotNull Disposable parentDisposable) {
        listeners.add(listener);
        Disposer.register(parentDisposable, () -> listeners.remove(listener));
    }

    private void attachProcessListener(@NotNull ProcessHandler handler) {
        handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull com.intellij.openapi.util.Key outputType) {
                if (!enabled) {
                    return;
                }
                handleLine(handler, event.getText());
            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                pendingSqlByProcess.remove(handler);
            }
        });
    }

    private void handleLine(@NotNull ProcessHandler handler, @NotNull String rawLine) {
        String line = rawLine.strip();
        if (line.isEmpty()) {
            return;
        }

        String preparingSql = extractAfter(line, PREPARING_KEYWORD);
        if (preparingSql != null) {
            pendingSqlByProcess.put(handler, preparingSql);
            if (!preparingSql.contains("?")) {
                appendEntry(formatEntry(preparingSql, null, preparingSql));
            }
            return;
        }

        String parameters = extractAfter(line, PARAMETERS_KEYWORD);
        if (parameters != null) {
            String preparing = pendingSqlByProcess.remove(handler);
            if (preparing != null) {
                String readable = renderSql(preparing, parameters);
                appendEntry(formatEntry(preparing, parameters, readable));
            }
        }
    }

    private String extractAfter(String line, String keyword) {
        int idx = line.indexOf(keyword);
        if (idx < 0) {
            return null;
        }
        return line.substring(idx + keyword.length()).trim();
    }

    private String renderSql(String preparingSql, String parameters) {
        List<String> params = parseParameters(parameters);
        Matcher matcher = PLACEHOLDER.matcher(preparingSql);
        StringBuffer sb = new StringBuffer(preparingSql.length() + 64);
        int index = 0;
        while (matcher.find()) {
            String replacement = index < params.size() ? params.get(index) : "?";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            index++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private List<String> parseParameters(String parameters) {
        List<String> parts = splitParameters(parameters);
        List<String> sqlLiterals = new ArrayList<>(parts.size());
        for (String part : parts) {
            sqlLiterals.add(toSqlLiteral(part));
        }
        return sqlLiterals;
    }

    private List<String> splitParameters(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return result;
        }
        StringBuilder token = new StringBuilder();
        int parenthesisLevel = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '(') {
                parenthesisLevel++;
            } else if (c == ')' && parenthesisLevel > 0) {
                parenthesisLevel--;
            } else if (c == ',' && parenthesisLevel == 0) {
                addToken(result, token);
                token.setLength(0);
                continue;
            }
            token.append(c);
        }
        addToken(result, token);
        return result;
    }

    private void addToken(List<String> result, StringBuilder token) {
        String item = token.toString().trim();
        if (!item.isEmpty()) {
            result.add(item);
        }
    }

    private String toSqlLiteral(String raw) {
        String value = raw;
        String type = "";
        Matcher matcher = PARAM_TYPE.matcher(raw);
        if (matcher.matches()) {
            value = matcher.group(1).trim();
            type = matcher.group(2).trim().toLowerCase();
        }

        if ("null".equalsIgnoreCase(value)) {
            return "NULL";
        }
        if (type.contains("bool")) {
            return "true".equalsIgnoreCase(value) ? "TRUE" : "FALSE";
        }
        if (type.contains("int") || type.contains("long")
                || type.contains("short") || type.contains("byte")
                || type.contains("float") || type.contains("double")
                || type.contains("bigdecimal") || type.contains("biginteger")) {
            return value;
        }
        if (type.contains("date") || type.contains("time")
                || type.contains("string") || type.contains("char")
                || type.contains("uuid")) {
            return quote(value);
        }
        return quote(value);
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String formatEntry(String preparing, String parameters, String readable) {
        StringBuilder entry = new StringBuilder(256);
        entry.append('[').append(LocalTime.now().format(TIME_FORMAT)).append("] ").append("MyBatis SQL").append('\n');
        entry.append("SQL: ").append(readable).append('\n');
        if (parameters != null && !parameters.isBlank()) {
            entry.append("Params: ").append(parameters).append('\n');
        }
        if (preparing != null && !preparing.equals(readable)) {
            entry.append("Template: ").append(preparing).append('\n');
        }
        entry.append("--------------------------------------------------").append('\n');
        return entry.toString();
    }

    private void appendEntry(String entry) {
        synchronized (bufferLock) {
            buffer.append(entry);
            if (buffer.length() > MAX_BUFFER_CHARS) {
                int overflow = buffer.length() - MAX_BUFFER_CHARS;
                buffer.delete(0, overflow);
            }
        }
        fireAppended(entry);
    }

    private void fireAppended(String text) {
        if (listeners.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Listener listener : listeners) {
                listener.onAppended(text);
            }
        });
    }

    private void fireCleared() {
        if (listeners.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Listener listener : listeners) {
                listener.onCleared();
            }
        });
    }

    private void fireEnabledChanged(boolean newState) {
        if (listeners.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Listener listener : listeners) {
                listener.onEnabledChanged(newState);
            }
        });
    }

    public interface Listener {
        void onAppended(@NotNull String text);

        void onCleared();

        void onEnabledChanged(boolean enabled);
    }
}
