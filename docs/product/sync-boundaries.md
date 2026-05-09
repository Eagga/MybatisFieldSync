# 同步边界

## 当前支持的主要片段

- `insert`
- batch insert（含 `foreach`）
- `update`
- `where`
- `base_column_list`
- `resultMap`

## 动态 SQL 支持范围

当前同步链路支持：

- 标准 `trim` / `set` / `where` 结构
- `if`
- `choose / when / otherwise`
- `foreach`
- 多层 `if/choose/foreach/trim` 嵌套分支补齐

实现核心位于：

- `service.FieldSyncService`
- `service.DynamicSqlStructureSupport`

## 当前边界

- 超复杂动态 SQL 若混入大量自定义脚本片段或非常规标签组合，仍建议先预览再执行
- `base_column_list` 目前依赖 `sql` 标签且 `id` 包含 `column` 的命名约定
- `resultMap` 当前重点是普通 `<result>` 增量补齐；更高级结构仍在路线图中

## 格式与映射约束

- 新增 XML 片段优先继承目标 XML 已有缩进、换行和逗号风格
- 探测不到现有风格时，回退到设置页中的 XML Formatting Strategy
- 参数占位符和 `resultMap` 输出会结合 JdbcType 与 TypeHandler 自定义映射

## 平台边界

- Database 集成需要 IntelliJ IDEA Ultimate
- 插件基础能力目标版本为 IDEA 2023.3+（`since-build=233`）
