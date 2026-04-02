package com.eagga.mybatisfieldsync.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DynamicSqlStructureSupport {
    private DynamicSqlStructureSupport() {
    }

    static @NotNull List<InsertTrimPlan> findInsertTrimPlans(@NotNull TagView root, boolean batch) {
        List<InsertTrimPlan> plans = new ArrayList<>();
        collectInsertTrimPlans(root, List.of(), batch, plans);
        return plans;
    }

    static @NotNull List<List<Integer>> findConditionalContainerPaths(@NotNull TagView root) {
        List<List<Integer>> paths = new ArrayList<>();
        collectConditionalContainerPaths(root, List.of(), paths);
        return paths;
    }

    private static void collectInsertTrimPlans(@NotNull TagView node,
            @NotNull List<Integer> path,
            boolean batch,
            @NotNull List<InsertTrimPlan> out) {
        List<? extends TagView> children = node.children();
        List<Integer> directTrimIndexes = new ArrayList<>();
        List<Integer> directForeachIndexes = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            String childName = normalize(children.get(i).name());
            if ("trim".equals(childName)) {
                directTrimIndexes.add(i);
            } else if ("foreach".equals(childName)) {
                directForeachIndexes.add(i);
            }
        }

        List<Integer> columnTrimIndexes = new ArrayList<>();
        List<Integer> valueTrimIndexes = new ArrayList<>();
        for (Integer trimIndex : directTrimIndexes) {
            if (containsPlaceholder(children.get(trimIndex).text())) {
                valueTrimIndexes.add(trimIndex);
            } else {
                columnTrimIndexes.add(trimIndex);
            }
        }

        if (!columnTrimIndexes.isEmpty() && !valueTrimIndexes.isEmpty()) {
            for (Integer columnIndex : columnTrimIndexes) {
                Integer valueIndex = findNearestRightValueTrim(valueTrimIndexes, columnIndex);
                if (valueIndex == null) {
                    continue;
                }
                out.add(new InsertTrimPlan(append(path, columnIndex), append(path, valueIndex), null));
            }
        } else if (batch && columnTrimIndexes.size() == 1 && !directForeachIndexes.isEmpty()) {
            for (Integer foreachIndex : directForeachIndexes) {
                List<Integer> valueTrimPath = findFirstDescendantValueTrimPath(children.get(foreachIndex),
                        append(path, foreachIndex));
                if (valueTrimPath == null) {
                    continue;
                }
                out.add(new InsertTrimPlan(
                        append(path, columnTrimIndexes.get(0)),
                        valueTrimPath,
                        children.get(foreachIndex).attribute("item")));
            }
        }

        for (int i = 0; i < children.size(); i++) {
            String childName = normalize(children.get(i).name());
            if (isDynamicContainer(childName)) {
                collectInsertTrimPlans(children.get(i), append(path, i), batch, out);
            }
        }
    }

    private static void collectConditionalContainerPaths(@NotNull TagView node,
            @NotNull List<Integer> path,
            @NotNull List<List<Integer>> out) {
        List<? extends TagView> children = node.children();
        if (children.isEmpty()) {
            out.add(path);
            return;
        }

        boolean hasDirectIfChildren = children.stream().anyMatch(child -> "if".equals(normalize(child.name())));
        if (hasDirectIfChildren) {
            out.add(path);
            return;
        }

        List<Integer> branchIndexes = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            String childName = normalize(children.get(i).name());
            if (isConditionalBranchContainer(childName)) {
                branchIndexes.add(i);
            }
        }
        if (!branchIndexes.isEmpty()) {
            for (Integer branchIndex : branchIndexes) {
                collectConditionalContainerPaths(children.get(branchIndex), append(path, branchIndex), out);
            }
            return;
        }

        out.add(path);
    }

    private static @Nullable Integer findNearestRightValueTrim(@NotNull List<Integer> valueIndexes, int columnIndex) {
        for (Integer valueIndex : valueIndexes) {
            if (valueIndex > columnIndex) {
                return valueIndex;
            }
        }
        return valueIndexes.isEmpty() ? null : valueIndexes.get(0);
    }

    private static @Nullable List<Integer> findFirstDescendantValueTrimPath(@NotNull TagView node,
            @NotNull List<Integer> path) {
        if ("trim".equals(normalize(node.name())) && containsPlaceholder(node.text())) {
            return path;
        }
        List<? extends TagView> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            List<Integer> found = findFirstDescendantValueTrimPath(children.get(i), append(path, i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean containsPlaceholder(@Nullable String text) {
        return text != null && text.contains("#{");
    }

    private static boolean isDynamicContainer(@NotNull String tagName) {
        return switch (tagName) {
            case "insert", "trim", "if", "choose", "when", "otherwise", "foreach" -> true;
            default -> false;
        };
    }

    private static boolean isConditionalBranchContainer(@NotNull String tagName) {
        return switch (tagName) {
            case "choose", "when", "otherwise", "trim", "where", "set" -> true;
            default -> false;
        };
    }

    private static @NotNull String normalize(@Nullable String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static @NotNull List<Integer> append(@NotNull List<Integer> path, int index) {
        List<Integer> copy = new ArrayList<>(path.size() + 1);
        copy.addAll(path);
        copy.add(index);
        return copy;
    }

    record InsertTrimPlan(@NotNull List<Integer> columnPath,
                          @NotNull List<Integer> valuePath,
                          @Nullable String foreachItem) {
    }

    interface TagView {
        @NotNull String name();

        @NotNull String text();

        @NotNull List<? extends TagView> children();

        @Nullable String attribute(@NotNull String name);
    }
}
