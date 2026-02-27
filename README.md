# MyBatis Field Sync

`MyBatis Field Sync` 是一个 IntelliJ IDEA 插件，用于从 Java 实体类中选择字段，并将字段同步到 MyBatis Mapper XML 的指定 Statement（`insert` / `update` / `base_column_list`）。

## 功能特性

- 右键触发菜单：`Sync Fields to MyBatis XML`
- 仅在 Java 编辑器右键菜单中显示，且限制在类名或空白位置
- 字段选择对话框：
  - 当前类字段
  - 可选包含父类字段
  - 字段勾选、全选、全不选
  - 展示字段名与字段类型
- 自动查找 Mapper XML：
  - 按类名同名 XML（`ClassName.xml`）
  - 按 `ClassNameMapper.xml`
  - 按 `mapper namespace` 与类名/全限定名匹配
- Statement 选择：自动读取 XML 中可用的 `id`
- Statement 支持多选（可一次同步多个 insert/update/base_column_list）
- 同步策略：
  - `insert`：优先对 `<trim>` 的列和值做增量补齐并保持对应关系；若已使用 `<if>`，新增项同样使用 `<if>` 风格；无 `trim` 时回退更新 `VALUES` 括号块
  - `update`：优先对 `<set>` 增量补齐；若已使用 `<if>`，新增项同样使用 `<if>` 风格；无 `<set>` 时回退更新 `SET ... WHERE` 区段
  - `base_column_list`：对 `<sql id="...">` 列表做增量补齐
- 字段格式转换：
  - `userName -> user_name`
  - 参数占位符：`#{userName,jdbcType=VARCHAR}`
- 常用 Java -> JdbcType 映射内置
- 使用 IDEA Notification 气泡反馈成功/失败
- 所有写操作包裹在 `WriteCommandAction` 中，支持 Undo
- 支持 i18n（英文/中文）

## 技术栈

- Java 17+
- Gradle Kotlin DSL
- IntelliJ Platform Plugin SDK（基于 IDEA 2023.3，`since-build=233`）
- IntelliJ PSI / Swing UI / Notification API

## 工程结构

```text
mybatis-field-sync
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/main/java/com/eagga/mybatisfieldsync
│   ├── action
│   │   └── SyncFieldsAction.java
│   ├── i18n
│   │   └── MyBatisFieldSyncBundle.java
│   ├── model
│   │   ├── FieldInfo.java
│   │   ├── StatementInfo.java
│   │   └── SyncException.java
│   ├── service
│   │   └── FieldSyncService.java
│   ├── ui
│   │   ├── FieldSelectionDialog.java
│   │   ├── FieldSelectionTableModel.java
│   │   ├── SimpleStatementRenderer.java
│   │   └── SimpleXmlFileRenderer.java
│   └── util
│       ├── IndentUtil.java
│       ├── JdbcTypeUtil.java
│       ├── NameUtil.java
│       └── NotificationUtil.java
└── src/main/resources
    ├── META-INF/plugin.xml
    └── messages
        ├── MyBatisFieldSyncBundle.properties
        └── MyBatisFieldSyncBundle_zh_CN.properties
```

## 安装与运行

### 1. 在 IntelliJ 中打开项目

使用 IDEA 打开项目根目录。

### 2. 运行插件开发实例

如果本地有 Gradle 环境或已生成 Wrapper：

```bash
./gradlew runIde
```

会启动一个沙箱 IDEA 实例用于调试插件。

### 3. 打包插件

```bash
./gradlew buildPlugin
```

输出 ZIP 位于 `build/distributions/`。

## 使用说明

1. 打开 Java 实体类文件。
2. 在编辑器类名处或空白处右键，点击 `Sync Fields to MyBatis XML`。
3. 在弹窗中：
   - 选择目标 XML 文件
   - 选择目标 Statement ID
   - 勾选要同步的字段（可全选/全不选）
   - 可切换是否包含父类字段
4. 点击 OK，插件执行同步并提示结果。

## 核心架构

- `SyncFieldsAction`
  - 控制菜单显示条件
  - 解析当前目标类
  - 打开字段选择对话框
  - 调用 Service 执行同步
- `FieldSelectionDialog`
  - 字段/目标 XML/Statement 交互入口
- `FieldSyncService`（Project Service）
  - 收集字段（含继承链）
  - 搜索候选 XML
  - 收集可选 Statement
  - 按 Statement 类型执行同步写入
- `util/*`
  - 驼峰转下划线
  - Java/JdbcType 映射
  - 缩进探测
  - Notification 封装

## i18n

- 默认英文：`messages/MyBatisFieldSyncBundle.properties`
- 简体中文：`messages/MyBatisFieldSyncBundle_zh_CN.properties`
- 所有 UI 文案与提示统一通过 `MyBatisFieldSyncBundle.message(...)` 获取

IDEA 根据系统/IDE 语言自动选择资源包。

## 注意事项与限制

- 当前触发入口注册在编辑器右键菜单（`EditorPopupMenu`）。
- 复杂动态 SQL（多层 `if/choose/foreach`）场景下，会优先更新标准块（`trim` / `set`）；若结构非常规，可能需人工微调。
- `base_column_list` 通过 `sql` 标签 + `id` 包含 `column` 判定，若团队命名规则不同，可在 Service 中扩展。

## 后续可扩展方向

- 增加”保留现有字段并合并”模式
- 增加”按注解忽略字段”（如 `@Transient`）
- 增加对 `where` 条件片段同步
- 增加更多 Statement 类型模板（批量插入等）

## TODO List

### 高优先级
- [ ] 支持按注解忽略字段（如 `@TableField`、`@Transient`）
- [ ] 字段同步时保留已有字段的顺序合并新字段
- [ ] 支持 `resultMap` 的字段映射同步

### 中优先级
- [ ] 支持 `where` 条件片段同步
- [ ] 增加批量插入语句模板支持
- [ ] 支持自定义 JdbcType 映射配置
- [ ] 提供同步预览功能（先预览再执行）

### 低优先级
- [ ] 支持 `foreach` 批量插入场景
- [ ] 支持动态表名场景
- [ ] 添加快捷键支持
- [ ] 提供同步历史记录功能
- [ ] 支持多模块项目的 XML 自动查找
