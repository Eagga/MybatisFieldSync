package com.eagga.mybatisfieldsync.database;

import com.intellij.database.dataSource.DatabaseConnectionManager;
import com.intellij.database.dataSource.LocalDataSource;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbPsiFacade;
import com.intellij.database.util.DasUtil;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.DasColumn;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库连接服务，用于读取 IDEA Database 工具的连接信息。
 * 提供表结构、列信息等数据库元数据，用于增强代码补全和提示。
 */
@Service(Service.Level.PROJECT)
public final class DatabaseConnectionService {

    private final Project project;

    public DatabaseConnectionService(@NotNull Project project) {
        this.project = project;
    }

    /**
     * 获取所有可用的数据源列表
     */
    public @NotNull List<DbDataSource> getAllDataSources() {
        DbPsiFacade dbPsiFacade = DbPsiFacade.getInstance(project);
        return dbPsiFacade.getDataSources();
    }

    /**
     * 根据表名查找表信息（支持模糊匹配）
     */
    public @NotNull List<TableInfo> findTablesByName(@NotNull String tableName) {
        List<TableInfo> result = new ArrayList<>();
        String lowerTableName = tableName.toLowerCase();

        for (DbDataSource dataSource : getAllDataSources()) {
            DasUtil.getTables(dataSource).forEach(table -> {
                String name = table.getName();
                if (name != null && name.toLowerCase().contains(lowerTableName)) {
                    result.add(new TableInfo(
                            dataSource.getName(),
                            name,
                            table.getComment(),
                            extractColumns(table)
                    ));
                }
            });
        }

        return result;
    }

    /**
     * 根据实体类名推断表名并查找表信息
     * 支持驼峰转下划线：UserInfo -> user_info
     */
    public @Nullable TableInfo findTableByEntityName(@NotNull String entityClassName) {
        // 驼峰转下划线
        String tableName = camelToSnake(entityClassName);
        List<TableInfo> tables = findTablesByName(tableName);

        // 优先返回完全匹配的表
        for (TableInfo table : tables) {
            if (table.tableName().equalsIgnoreCase(tableName)) {
                return table;
            }
        }

        // 返回第一个匹配的表
        return tables.isEmpty() ? null : tables.get(0);
    }

    /**
     * 获取指定表的所有列信息
     */
    public @NotNull List<ColumnInfo> getTableColumns(@NotNull String dataSourceName, @NotNull String tableName) {
        for (DbDataSource dataSource : getAllDataSources()) {
            if (dataSource.getName().equals(dataSourceName)) {
                for (DasTable table : DasUtil.getTables(dataSource)) {
                    if (tableName.equalsIgnoreCase(table.getName())) {
                        return extractColumns(table);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * 检查数据库插件是否可用
     */
    public boolean isDatabasePluginAvailable() {
        try {
            DbPsiFacade.getInstance(project);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 提取表的列信息
     */
    private @NotNull List<ColumnInfo> extractColumns(@NotNull DasTable table) {
        List<ColumnInfo> columns = new ArrayList<>();
        for (DasColumn column : DasUtil.getColumns(table)) {
            columns.add(new ColumnInfo(
                    column.getName(),
                    column.getDasType().toDataType().typeName,
                    column.getComment(),
                    DasUtil.isPrimary(column),
                    !column.isNotNull()
            ));
        }
        return columns;
    }

    /**
     * 驼峰转下划线
     */
    private @NotNull String camelToSnake(@NotNull String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 表信息记录
     */
    public record TableInfo(
            @NotNull String dataSourceName,
            @NotNull String tableName,
            @Nullable String comment,
            @NotNull List<ColumnInfo> columns
    ) {
    }

    /**
     * 列信息记录
     */
    public record ColumnInfo(
            @NotNull String columnName,
            @NotNull String dataType,
            @Nullable String comment,
            boolean isPrimaryKey,
            boolean isNullable
    ) {
    }
}
