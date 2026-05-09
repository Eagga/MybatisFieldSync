# MyBatis Field Sync

`MyBatis Field Sync` 是一个 IntelliJ IDEA 插件，用于在 Java 实体类与 MyBatis Mapper XML 之间做双向字段同步、注释同步与重构联动，支持 `insert` / `update` / `base_column_list` / `resultMap` 等常见片段。

## 文档导航

- 架构与目录：[`docs/architecture/project-map.md`](./docs/architecture/project-map.md)
- 包职责：[`docs/architecture/package-responsibilities.md`](./docs/architecture/package-responsibilities.md)
- 本地开发：[`docs/development/local-dev.md`](./docs/development/local-dev.md)
- 测试与验证：[`docs/development/testing-and-verification.md`](./docs/development/testing-and-verification.md)
- 发布与 Marketplace：[`docs/development/release-and-marketplace.md`](./docs/development/release-and-marketplace.md)
- 功能矩阵：[`docs/product/feature-matrix.md`](./docs/product/feature-matrix.md)
- 使用指南：[`docs/product/usage-guide.md`](./docs/product/usage-guide.md)
- 同步边界：[`docs/product/sync-boundaries.md`](./docs/product/sync-boundaries.md)
- 路线图与限制：[`docs/maintenance/roadmap.md`](./docs/maintenance/roadmap.md)、[`docs/maintenance/known-constraints.md`](./docs/maintenance/known-constraints.md)

## 功能特性

### 基础功能
- 右键触发菜单：`MyBatis Field Sync` -> `Sync Fields to XML`
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
  - 多模块重名 XML 冲突时按“同模块优先、依赖模块次之、其他模块最后”排序候选
- Statement 选择：自动读取 XML 中可用的 `id`（包括 `<resultMap>` 标签）
- Statement 支持多选（可一次同步多个 insert/update/base_column_list/resultMap）
- **批量同步向导（Batch Sync Wizard）**：
  - 在 Java 编辑器中右键 `MyBatis Field Sync` -> `Batch Sync Wizard`
  - 支持一次勾选多个实体类，逐项进入现有字段/Statement 选择与预览流程
  - 单项失败或跳过不会中断后续实体处理
  - 处理完成后展示最终汇总报告
- **XML 反向生成实体字段**：
  - 在 XML 编辑器中右键 `MyBatis Field Sync` -> `Generate Fields From XML`
  - 支持从当前选中的 `resultMap` 或 `base_column_list` 片段生成 Java 字段草稿
  - 支持预览生成内容后再写入实体类
  - 同名字段不会覆盖，预览与通知中会提示冲突字段
- **字段注释同步到 XML 注释**：
  - 在 Java 实体类中右键 `MyBatis Field Sync` -> `Sync Field Comments to XML`
  - 支持将 Java 字段 JavaDoc/紧邻注释同步到对应 SQL 列注释或 `<result>` 前注释
  - 保持 XML 中原有缩进和换行风格
- **字段重命名联动更新 XML**：
  - Java 字段执行 IDEA Rename 后，自动联动更新候选 Mapper XML 中的 `#{}`、`${}`、`<if test="">`、`<result property="">`
  - 走 `WriteCommandAction` / IDE Refactor 链路，支持 Undo / Redo

### 智能同步策略
- **`insert`**：优先对 `<trim>` 的列和值做增量补齐并保持对应关系；若已使用 `<if>`，新增项同样使用 `<if>` 风格；支持基于 `<foreach>` 的批量插入语句，并可识别多层 `choose/when/otherwise/foreach/trim` 嵌套分支
- **`update`**：优先对 `<set>` 增量补齐；若已使用 `<if>`，新增项同样使用 `<if>` 风格；无 `<set>` 时回退更新 `SET ... WHERE` 区段；支持在多层 `choose/when/otherwise/trim` 分支内按原结构补齐赋值项
- **`base_column_list`**：对 `<sql id=”...”>` 列表做增量补齐
- **`where`**：对包含 `<where>` 标签（或 ID 包含 `where`）的片段添加增量条件，支持在多层 `choose/when/otherwise/trim` 分支内补齐条件块
- **`resultMap`**：对 `<resultMap>` 内的 `<result>` 标签做增量补齐，自动生成 `column`、`property`、`jdbcType`，并按配置追加 `typeHandler` 属性

### 高级功能
- **JPA 风格方法名补全**：在 Mapper 接口中输入 `findBy`、`countBy`、`deleteBy`、`existsBy` 时自动提示字段组合，选择后自动生成方法签名和 XML SQL 语句
  - 支持单字段：`findByName`、`findByAge`
  - 支持操作符：`GreaterThan`、`LessThan`、`GreaterThanEqual`、`LessThanEqual`、`Like`、`NotLike`、`In`、`NotIn`、`Between`、`IsNull`、`IsNotNull`
  - 支持多字段组合：`findByNameAndAge`、`findByNameOrEmail`
  - 自动生成 `@Param` 注解和对应的 XML select/delete 语句（`Between` 生成 `xxxStart/xxxEnd`，`In/NotIn` 生成 `Collection<T>` 参数）
  - 自动过滤带 `@Transient` 或 `@TableField(exist=false)` 注解的字段
- **数据库连接集成**：自动读取 IDEA Database 工具的数据库连接信息，增强代码补全和类型映射
  - 根据实体类名自动匹配数据库表（支持驼峰转下划线）
  - 使用数据库列类型映射更准确的 JdbcType（支持 MySQL、PostgreSQL、Oracle、SQL Server）
  - 字段同步时优先使用数据库元数据进行类型推断
  - 需要 IntelliJ IDEA Ultimate 版本（Community 版本不支持 Database 插件）
- **MyBatis SQL 日志过滤预览（右侧工具窗）**：在 IDEA 右侧打开 `MyBatis SQL Preview`，可开启/关闭过滤，实时预览本地运行日志中的 MyBatis 实际执行 SQL，支持一键清空
- **CRUD 模板生成**：`MyBatis Field Sync` -> `Generate CRUD Template`，一键生成标准 CRUD 语句（INSERT、UPDATE、DELETE、SELECT、ResultMap）；模板默认使用动态表名 `${tableName}`，并在主键条件处优先采用 `@TableId`（无注解时回退到 `id` 字段）
- **XML 格式策略配置**：在 IDEA 设置（`Settings -> Tools -> MyBatis Field Sync`）中配置 XML 缩进、换行、逗号风格；插件新增片段会优先继承现有 XML 风格，未探测到时再回退到设置值
- **TypeHandler 自定义映射**：在 IDEA 设置中配置 `javaType=typeHandlerClass[,jdbcType=XXX]`；对 CRUD 模板生成和字段同步新增片段同时生效，并与已有 JdbcType 映射兼容
- **Mapper 接口方法生成**：`MyBatis Field Sync` -> `Generate Mapper Methods`，自动在 Mapper 接口中生成标准方法（insert、update、delete、selectById、selectList），自动查找对应的 Mapper 接口（支持 EntityMapper 和 EntityDao 命名规范）；`delete/selectById` 参数会优先采用 `@TableId` 的字段名与类型
- **同步历史记录**：`MyBatis Field Sync` -> `View Sync History` 查看所有同步操作历史，支持清空历史
- **快捷键支持**：
  - `Ctrl+Alt+S`：字段同步
  - `MyBatis Field Sync` -> `Batch Sync Wizard`：批量同步向导（菜单入口）
  - `Ctrl+Alt+G`：生成 CRUD 模板
  - `Ctrl+Alt+M`：生成 Mapper 方法
  - `Ctrl+Alt+H`：查看同步历史
- **智能导航**：Mapper 接口方法与 XML Statement 双向跳转（Gutter Icon）
- **SQL 语法检测**：MyBatis XML 中的 SQL 语句实时语法检查，错误标红提示（需启用 IDEA 的 Database Tools and SQL 插件）
- **参数智能处理**：自动将 `#{...}` 和 `${...}` 转换为 SQL 占位符进行语法分析
- **同步预览**：先预览目标文本、确认后再执行，防止误修改；仅预览/取消不会写入同步历史
- **反向字段草稿预览**：从 XML 反向生成实体字段时，先展示字段草稿和冲突列表，确认后再写入类中
- **字段过滤**：自动忽略带 `@TableField(exist=false)` 或 `@Transient` 注解的字段
- **MyBatis-Plus 注解支持（已实现范围）**：
  - `@TableName`：用于 SQL 生成与数据库表匹配时的表名解析（未标注时回退类名驼峰转下划线）
  - `@TableId`：用于 CRUD 条件与 Mapper 方法参数的主键解析
  - `@TableField(exist=false)`：用于字段过滤（不参与同步/补全）
- **自定义映射**：在 IDEA 设置（Settings -> Tools -> MyBatis Field Sync）中配置 `javaType=jdbcType` 与 `javaType=typeHandlerClass[,jdbcType=XXX]` 映射

### 开发体验
- 字段格式转换：`userName -> user_name`
- 参数占位符：`#{userName,jdbcType=VARCHAR}`
- TypeHandler 占位符：`#{createdAt,jdbcType=TIMESTAMP,typeHandler=com.demo.LocalDateTimeTypeHandler}`
- 常用 Java -> JdbcType 映射内置
- 使用 IDEA Notification 气泡反馈成功/失败
- 所有写操作包裹在 `WriteCommandAction` 中，支持 Undo
- 支持 i18n（英文/中文）

## 技术栈

- Java 17+（构建与运行插件开发环境）
- Gradle Kotlin DSL
- IntelliJ Platform Plugin SDK（兼容 IDEA 2023.3+，`since-build=233`，已覆盖 2024/2025）
- IntelliJ PSI / Swing UI / Notification API

## 版本兼容性

- IntelliJ IDEA：2023.3 及以上（包含 2024.x、2025.x）
- 插件声明：`since-build=233`，`until-build` 未限制
- Java（插件项目构建）：17
- 数据库集成功能：需要 IntelliJ IDEA Ultimate 版本（Community 版本不支持 Database 插件，但其他功能正常使用）

## 工程结构

仓库采用单模块 Gradle 结构，源码位于 `src/`，系统文档统一收敛到 `docs/`。

- 顶层结构与关键入口：[`docs/architecture/project-map.md`](./docs/architecture/project-map.md)
- 代码包职责：[`docs/architecture/package-responsibilities.md`](./docs/architecture/package-responsibilities.md)

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

### 4. 在 IDEA 2023.3+（含 2024/2025）安装本地 ZIP

1. 打开 `Settings` -> `Plugins` -> 右上角齿轮 -> `Install Plugin from Disk...`
2. 选择 `build/distributions/` 下最新 ZIP
3. 安装后重启 IDE
4. 若你之前装过旧版本，建议先卸载旧版本再安装新包，避免缓存旧类

## 自动发布到插件市场（GitHub Actions）

仓库已提供手动触发的发布工作流：`.github/workflows/publish-plugin.yml`。

### 1. 配置 GitHub Secrets

在仓库 `Settings -> Secrets and variables -> Actions` 中添加：

- `PUBLISH_TOKEN`：JetBrains Marketplace 发布 Token
- `CERTIFICATE_CHAIN`：插件签名证书链（PEM）
- `PRIVATE_KEY`：签名私钥（PEM）
- `PRIVATE_KEY_PASSWORD`：私钥密码

### 2. 手动触发发布

1. 打开 GitHub 仓库 `Actions` 页面
2. 选择工作流 `Publish Plugin`
3. 点击 `Run workflow`
4. 输入：
   - `version`：发布版本号（如 `1.0.1`）
   - `channel`：发布通道（默认 `default`，预发布可用 `eap`）
5. 确认后工作流会自动执行 `clean -> verifyPlugin -> signPlugin -> publishPlugin`

### 3. 本地可选验证命令

```bash
./gradlew verifyPlugin
```

```bash
PLUGIN_VERSION=1.0.1 PUBLISH_CHANNEL=default ./gradlew signPlugin publishPlugin
```

> 注意：第二条命令需要你在环境变量或 `~/.gradle/gradle.properties` 中提供发布与签名凭据。

## License

本项目已在 GitHub 开源，采用 **Apache License 2.0**。

- 许可证全文见：[LICENSE](./LICENSE)
- 你可以在遵守 Apache-2.0 条款的前提下使用、修改和分发本项目代码
- 若你基于本项目做二次分发，请保留许可证与相关版权说明

## 使用说明

详细操作步骤、快捷键、补全模式和交互流程已迁移到：

- [`docs/product/usage-guide.md`](./docs/product/usage-guide.md)

常用入口如下：

- `MyBatis Field Sync -> Sync Fields to XML`
- `MyBatis Field Sync -> Batch Sync Wizard`
- `MyBatis Field Sync -> Generate CRUD Template`
- `MyBatis Field Sync -> Generate Fields From XML`
- `MyBatis Field Sync -> View Sync History`

### 常见场景

典型示例和完整场景说明已迁移到：

- [`docs/product/usage-guide.md`](./docs/product/usage-guide.md)

## 测试

常用测试与验证命令已迁移到：

- [`docs/development/testing-and-verification.md`](./docs/development/testing-and-verification.md)

最小编译检查：

```bash
./gradlew compileJava --console=plain
```

## 核心架构

核心架构说明已拆分到：

- 项目地图：[`docs/architecture/project-map.md`](./docs/architecture/project-map.md)
- 包职责：[`docs/architecture/package-responsibilities.md`](./docs/architecture/package-responsibilities.md)
- 功能矩阵：[`docs/product/feature-matrix.md`](./docs/product/feature-matrix.md)

## i18n

- 默认英文：`messages/MyBatisFieldSyncBundle.properties`
- 简体中文：`messages/MyBatisFieldSyncBundle_zh_CN.properties`
- 所有 UI 文案与提示统一通过 `MyBatisFieldSyncBundle.message(...)` 获取

IDEA 根据系统/IDE 语言自动选择资源包。

## 注意事项与限制

### 当前限制
- 触发入口注册在编辑器右键菜单（`EditorPopupMenu`）
- 超复杂动态 SQL 场景虽已支持多层 `if/choose/foreach/trim` 分支补齐，但若同一语句混合大量自定义脚本片段或非常规标签组合，仍建议先预览再执行
- `base_column_list` 通过 `sql` 标签 + `id` 包含 `column` 判定，若团队命名规则不同，可在 Service 中扩展
- 更完整的边界说明见：[`docs/product/sync-boundaries.md`](./docs/product/sync-boundaries.md)

### 最佳实践
1. **字段命名规范**：使用驼峰命名，插件自动转换为下划线（`userName` → `user_name`）
2. **XML 结构建议**：优先使用 `<trim>`、`<set>`、`<where>` 等标准标签，便于插件识别
3. **预览功能**：首次使用建议开启预览，确认同步结果符合预期
4. **注解过滤**：使用 `@Transient` 或 `@TableField(exist=false)` 标记非数据库字段
5. **批量操作**：支持一次选择多个 Statement 同步，提升效率

### 常见问题

**Q: 为什么某些字段没有同步？**
A: 检查字段是否被 `@Transient` 或 `@TableField(exist=false)` 注解标记，或者是 `static` 字段

**Q: 同步后 SQL 格式不符合团队规范？**
A: 插件会优先继承目标 XML 已有的缩进、换行与逗号风格；若当前片段探测不到风格，则回退到设置页中的 XML Formatting Strategy 配置

**Q: 支持哪些 JdbcType？**
A: 内置常用类型映射（String→VARCHAR、Integer→INTEGER 等），可在设置中自定义扩展

**Q: TypeHandler 怎么配置？**
A: 在 `Settings -> Tools -> MyBatis Field Sync` 的 `Custom TypeHandler Mapping` 中按行配置：

```text
java.time.LocalDateTime=com.demo.LocalDateTimeTypeHandler,jdbcType=TIMESTAMP
com.demo.Money=com.demo.MoneyTypeHandler,jdbcType=DECIMAL
```

生成的占位符会类似：

```xml
#{createdAt,jdbcType=TIMESTAMP,typeHandler=com.demo.LocalDateTimeTypeHandler}
```

对应的 `resultMap` 也会自动附带：

```xml
<result column="created_at" property="createdAt" jdbcType="TIMESTAMP" typeHandler="com.demo.LocalDateTimeTypeHandler"/>
```

**Q: 如何处理复杂的动态 SQL？**
A: 插件会尽量保持原有结构，对于特别复杂的场景建议先预览再执行

**Q: SQL 日志过滤预览没有内容？**
A: 先确认工具窗开关已开启；其次确认运行日志中包含 MyBatis 的 `Preparing:` / `Parameters:` 输出（如需可在项目日志配置中开启 SQL 日志级别）

**Q: Mapper 里输入 `findBy` 没有补全提示？**
A: 请先确认以下条件：
1. 当前文件是 Java Mapper 接口（`interface`）
2. 光标在方法名标识符位置（例如 `List<User> findBy|`）
3. 手动按 `Ctrl+Space` 触发补全（部分 IDE 配置会关闭自动弹出）
4. Mapper 能解析到实体：优先泛型（`BaseMapper<User>`），或命名规则（`UserMapper` 对应 `User`）
5. 已安装的是最新插件 ZIP（不是旧缓存版本）

**Q: IntelliJ IDEA 2023.3+（含 2024/2025）安装后功能异常？**
A: 先确认插件版本是否为你最新打包产物，并完成 IDE 重启；若仍异常，建议：
1. 卸载旧插件后重新安装 ZIP
2. `File -> Invalidate Caches...` 后重启
3. 在 Mapper 中用 `Ctrl+Space` 手动触发一次补全进行验证

## 后续可扩展方向

后续规划已迁移到：

- [`docs/maintenance/roadmap.md`](./docs/maintenance/roadmap.md)

## TODO List

详细 TODO 和优先级拆分已迁移到：

- [`docs/maintenance/roadmap.md`](./docs/maintenance/roadmap.md)
