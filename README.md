# MyBatis Field Sync

`MyBatis Field Sync` 是一个 IntelliJ IDEA 插件，用于从 Java 实体类中选择字段，并将字段同步到 MyBatis Mapper XML 的指定 Statement（`insert` / `update` / `base_column_list`）。

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
- Statement 选择：自动读取 XML 中可用的 `id`（包括 `<resultMap>` 标签）
- Statement 支持多选（可一次同步多个 insert/update/base_column_list/resultMap）

### 智能同步策略
- **`insert`**：优先对 `<trim>` 的列和值做增量补齐并保持对应关系；若已使用 `<if>`，新增项同样使用 `<if>` 风格；支持基于 `<foreach>` 的批量插入语句
- **`update`**：优先对 `<set>` 增量补齐；若已使用 `<if>`，新增项同样使用 `<if>` 风格；无 `<set>` 时回退更新 `SET ... WHERE` 区段
- **`base_column_list`**：对 `<sql id=”...”>` 列表做增量补齐
- **`where`**：对包含 `<where>` 标签（或 ID 包含 `where`）的片段添加增量条件
- **`resultMap`**：对 `<resultMap>` 内的 `<result>` 标签做增量补齐，自动生成 `column`、`property`、`jdbcType` 属性

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
- **CRUD 模板生成**：`MyBatis Field Sync` -> `Generate CRUD Template`，一键生成标准 CRUD 语句（INSERT、UPDATE、DELETE、SELECT、ResultMap），支持动态表名 `${tableName}`
- **Mapper 接口方法生成**：`MyBatis Field Sync` -> `Generate Mapper Methods`，自动在 Mapper 接口中生成标准方法（insert、update、delete、selectById、selectList），自动查找对应的 Mapper 接口（支持 EntityMapper 和 EntityDao 命名规范）
- **同步历史记录**：`MyBatis Field Sync` -> `View Sync History` 查看所有同步操作历史，支持清空历史
- **快捷键支持**：
  - `Ctrl+Alt+S`：字段同步
  - `Ctrl+Alt+G`：生成 CRUD 模板
  - `Ctrl+Alt+M`：生成 Mapper 方法
  - `Ctrl+Alt+H`：查看同步历史
- **智能导航**：Mapper 接口方法与 XML Statement 双向跳转（Gutter Icon）
- **SQL 语法检测**：MyBatis XML 中的 SQL 语句实时语法检查，错误标红提示（需启用 IDEA 的 Database Tools and SQL 插件）
- **参数智能处理**：自动将 `#{...}` 和 `${...}` 转换为 SQL 占位符进行语法分析
- **同步预览**：先预览目标文本、确认后再执行，防止误修改
- **字段过滤**：自动忽略带 `@TableField(exist=false)` 或 `@Transient` 注解的字段
- **自定义映射**：在 IDEA 设置（Settings -> Tools -> MyBatis Field Sync）中配置 `javaType=jdbcType` 映射

### 开发体验
- 字段格式转换：`userName -> user_name`
- 参数占位符：`#{userName,jdbcType=VARCHAR}`
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

```text
mybatis-field-sync
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/main/java/com/eagga/mybatisfieldsync
│   ├── action
│   │   ├── SyncFieldsAction.java
│   │   ├── GenerateCrudAction.java
│   │   └── ViewSyncHistoryAction.java
│   ├── completion
│   │   ├── MapperMethodCompletionContributor.java
│   │   ├── MapperMethodInsertHandler.java
│   │   ├── MethodNameParser.java
│   │   ├── SqlGenerator.java
│   │   └── XmlStatementGenerator.java
│   ├── database
│   │   ├── DatabaseConnectionService.java
│   │   ├── DatabaseTypeMapper.java
│   │   └── DatabaseFieldEnhancer.java
│   ├── i18n
│   │   └── MyBatisFieldSyncBundle.java
│   ├── injector
│   │   └── MyBatisSqlLanguageInjector.java
│   ├── marker
│   │   ├── MapperLineMarkerProvider.java
│   │   └── XmlLineMarkerProvider.java
│   ├── model
│   │   ├── FieldInfo.java
│   │   ├── StatementInfo.java
│   │   └── SyncException.java
│   ├── service
│   │   ├── FieldSyncService.java
│   │   ├── CrudTemplateService.java
│   │   ├── SyncHistoryService.java
│   │   └── SqlLogPreviewService.java
│   ├── settings
│   │   ├── MyBatisFieldSyncSettings.java
│   │   └── MyBatisFieldSyncConfigurable.java
│   ├── toolwindow
│   │   ├── SqlLogPreviewToolWindowFactory.java
│   │   └── SqlLogPreviewPanel.java
│   ├── ui
│   │   ├── FieldSelectionDialog.java
│   │   ├── FieldSelectionTableModel.java
│   │   ├── PreviewDialog.java
│   │   ├── CrudTemplateDialog.java
│   │   ├── SyncHistoryDialog.java
│   │   ├── SimpleStatementRenderer.java
│   │   └── SimpleXmlFileRenderer.java
│   └── util
│       ├── IndentUtil.java
│       ├── JdbcTypeUtil.java
│       ├── NameUtil.java
│       ├── FieldIgnoreUtil.java
│       └── NotificationUtil.java
├── src/test/java/com/eagga/mybatisfieldsync/completion
│   ├── MethodNameParserTest.java
│   └── SqlGeneratorTest.java
└── src/main/resources
    ├── META-INF
    │   ├── plugin.xml
    │   ├── database-support.xml
    │   └── pluginIcon.svg
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

### 4. 在 IDEA 2023.3+（含 2024/2025）安装本地 ZIP

1. 打开 `Settings` -> `Plugins` -> 右上角齿轮 -> `Install Plugin from Disk...`
2. 选择 `build/distributions/` 下最新 ZIP
3. 安装后重启 IDE
4. 若你之前装过旧版本，建议先卸载旧版本再安装新包，避免缓存旧类

## 使用说明

### 基础使用

#### 数据库连接配置（可选，增强功能）
1. 打开 IDEA 的 Database 工具窗口（View -> Tool Windows -> Database）
2. 添加数据源连接（支持 MySQL、PostgreSQL、Oracle、SQL Server 等）
3. 测试连接成功后，插件会自动读取表结构信息
4. 字段同步时会使用数据库列类型进行更准确的 JdbcType 映射
5. 注意：此功能需要 IntelliJ IDEA Ultimate 版本

#### 字段同步
1. 打开 Java 实体类文件
2. 在编辑器类名处或空白处右键，选择 `MyBatis Field Sync` -> `Sync Fields to XML`（或按 `Ctrl+Alt+S`）
3. 在弹窗中：
   - 选择目标 XML 文件
   - 选择目标 Statement ID（支持多选）
   - 勾选要同步的字段（可全选/全不选）
   - 可切换是否包含父类字段
4. 预览同步结果，确认后执行

#### CRUD 模板生成
1. 打开 Java 实体类文件
2. 在编辑器类名处或空白处右键，选择 `MyBatis Field Sync` -> `Generate CRUD Template`（或按 `Ctrl+Alt+G`）
3. 选择要生成的模板（ResultMap、Insert、Update、Delete、Select）
4. 自动在对应的 Mapper XML 中生成标准 CRUD 语句（支持动态表名 `${tableName}`）

#### 快捷键使用
- **字段同步**：`Ctrl+Alt+S` - 快速打开字段同步对话框
- **CRUD 生成**：`Ctrl+Alt+G` - 快速打开 CRUD 模板生成对话框
- **查看历史**：`Ctrl+Alt+H` - 查看所有同步操作历史记录

#### 同步历史记录
1. 右键选择 `MyBatis Field Sync` -> `View Sync History` 或按 `Ctrl+Alt+H`
2. 查看所有同步操作的时间、实体类、XML 文件、Statement ID 和字段列表
3. 可点击 `Clear History` 清空历史记录

#### JPA 风格方法名补全
1. 在 **Mapper 接口** 中可用两种方式触发：
   - 方式 A（骨架模式）：先写返回值再输入方法名前缀，例如 `List<Activity> findBy`
   - 方式 B（裸输入模式）：直接输入 `findBy` / `countBy` / `deleteBy` / `existsBy`
2. 若未自动弹出，请手动按 `Ctrl+Space` 触发 Basic Completion
3. 选择需要的方法名（如 `findByNameAndAge`）
4. 自动生成方法签名和对应的 XML SQL 语句
   - 方式 A：仅补全方法名与参数签名
   - 方式 B：自动补全完整方法声明（包含返回值）

实体类解析规则（按优先级）：
1. 优先从 Mapper 父接口泛型中解析实体，例如 `BaseMapper<User>`
2. 若无泛型信息，则按接口名推断：`UserMapper` -> `User`
3. 按实体短类名在项目内搜索并匹配

#### MyBatis SQL 日志过滤预览（右侧入口）
1. 在 IDEA 右侧工具栏打开 `MyBatis SQL Preview`
2. 勾选 `Enable SQL Log Filter`（或中文环境下“启用 SQL 日志过滤”）
3. 运行本地项目后，面板会从运行日志中过滤 `Preparing` / `Parameters` 并展示可读 SQL
4. 点击 `Clear` 可清空预览文本框
5. 取消勾选后停止采集与追加

### 常见场景

#### 场景 1：新增字段到已有 INSERT 语句
```java
// 实体类新增字段
public class User {
    private Long id;
    private String name;
    private String email;  // 新增
    private Integer age;   // 新增
}
```
右键同步后自动更新 XML：
```xml
<insert id="insert">
    INSERT INTO user (id, name, email, age)
    VALUES (#{id}, #{name}, #{email}, #{age})
</insert>
```

#### 场景 2：动态 UPDATE 语句增量补齐
```xml
<!-- 原有 -->
<update id="update">
    UPDATE user
    <set>
        <if test="name != null">name = #{name},</if>
    </set>
    WHERE id = #{id}
</update>

<!-- 同步后自动添加新字段，保持 if 风格 -->
<update id="update">
    UPDATE user
    <set>
        <if test="name != null">name = #{name},</if>
        <if test="email != null">email = #{email},</if>
        <if test="age != null">age = #{age},</if>
    </set>
    WHERE id = #{id}
</update>
```

#### 场景 3：ResultMap 字段映射同步
```xml
<!-- 自动生成完整的字段映射，JdbcType 根据数据库列类型自动推断 -->
<resultMap id="BaseResultMap" type="User">
    <result column="id" property="id" jdbcType="BIGINT"/>
    <result column="name" property="name" jdbcType="VARCHAR"/>
    <result column="email" property="email" jdbcType="VARCHAR"/>
    <result column="age" property="age" jdbcType="INTEGER"/>
</resultMap>
```

#### 场景 4：数据库集成增强类型映射
```java
// 实体类
public class User {
    private Long id;
    private String name;
    private LocalDateTime createTime;  // Java 类型
}
```

配置 Database 连接后，插件会读取数据库表结构：
```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    create_time DATETIME  -- 数据库类型
);
```

同步时自动使用数据库类型映射：
```xml
<insert id="insert">
    INSERT INTO user (id, name, create_time)
    VALUES (#{id,jdbcType=BIGINT}, #{name,jdbcType=VARCHAR}, #{createTime,jdbcType=TIMESTAMP})
</insert>
```

#### 场景 5：一键生成 CRUD 模板（支持动态表名）
```java
// 实体类
public class User {
    private Long id;
    private String name;
    private String email;
    private Integer age;
}
```
右键选择 `Generate MyBatis CRUD Template`，自动生成：
```xml
<resultMap id="BaseResultMap" type="com.example.User">
    <result column="id" property="id" jdbcType="BIGINT"/>
    <result column="name" property="name" jdbcType="VARCHAR"/>
    <result column="email" property="email" jdbcType="VARCHAR"/>
    <result column="age" property="age" jdbcType="INTEGER"/>
</resultMap>

<insert id="insert">
    INSERT INTO ${tableName} (id, name, email, age)
    VALUES (#{id,jdbcType=BIGINT}, #{name,jdbcType=VARCHAR}, #{email,jdbcType=VARCHAR}, #{age,jdbcType=INTEGER})
</insert>

<update id="update">
    UPDATE ${tableName}
    <set>
        <if test="id != null">id = #{id,jdbcType=BIGINT},</if>
        <if test="name != null">name = #{name,jdbcType=VARCHAR},</if>
        <if test="email != null">email = #{email,jdbcType=VARCHAR},</if>
        <if test="age != null">age = #{age,jdbcType=INTEGER},</if>
    </set>
    WHERE id = #{id}
</update>

<delete id="delete">
    DELETE FROM ${tableName} WHERE id = #{id}
</delete>

<select id="selectById" resultMap="BaseResultMap">
    SELECT id, name, email, age FROM ${tableName} WHERE id = #{id}
</select>
```

**动态表名使用**：在 Mapper 接口方法中传入 `tableName` 参数即可动态指定表名，适用于分表场景。

#### 场景 5：JPA 风格方法名自动补全
```java
// Mapper 接口
public interface UserMapper {
    // 输入 findBy 后自动提示：findByName, findByAge, findByNameAndAge 等
    // 选择 findByNameAndAge 后自动生成：
    List<User> findByNameAndAge(@Param("name") String name, @Param("age") Integer age);
}
```
自动生成的 XML：
```xml
<select id="findByNameAndAge" resultMap="BaseResultMap">
    SELECT * FROM user WHERE name = #{name} AND age = #{age}
</select>
```

支持的方法前缀和操作符：
- `findByAgeGreaterThan` → `WHERE age > #{age}`
- `countByNameLike` → `SELECT COUNT(*) FROM user WHERE name LIKE #{name}`
- `deleteByIdIn` → `DELETE FROM user WHERE id IN <foreach ...>#{item}</foreach>`
- `findByCreatedAtBetween` → `WHERE created_at BETWEEN #{createdAtStart} AND #{createdAtEnd}`
- `existsByDeletedAtIsNull` → `SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END ...`
- `findByNameOrEmail` → `WHERE name = #{name} OR email = #{email}`

## 测试

### 单元测试
```bash
# 运行补全链路相关测试
./gradlew test --tests "com.eagga.mybatisfieldsync.completion.*" -x instrumentCode -x instrumentTestCode -x classpathIndexCleanup
```

### 编译检查
```bash
./gradlew compileJava
```

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

### 当前限制
- 触发入口注册在编辑器右键菜单（`EditorPopupMenu`）
- 复杂动态 SQL（多层 `if/choose/foreach`）场景下，会优先更新标准块（`trim` / `set`）；若结构非常规，可能需人工微调
- `base_column_list` 通过 `sql` 标签 + `id` 包含 `column` 判定，若团队命名规则不同，可在 Service 中扩展

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
A: 插件会保持原有缩进风格，如需调整可在设置中配置或手动格式化

**Q: 支持哪些 JdbcType？**
A: 内置常用类型映射（String→VARCHAR、Integer→INTEGER 等），可在设置中自定义扩展

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

### 代码生成增强
- 从 XML 反向生成实体类字段
- 生成常用 CRUD 模板（增删改查）
- 生成 Mapper 接口方法声明
- 批量同步多个实体类

### 智能重构
- 字段重命名时自动更新 XML 中的引用
- 字段注释同步到 XML 注释
- XML 格式化配置（缩进、换行风格）

### 框架集成
- MyBatis-Plus 注解支持（`@TableName`、`@TableId`、`@TableField`）
- TypeHandler 自定义类型映射
- 支持 `<choose>/<when>/<otherwise>` 复杂动态标签

### 用户体验
- 添加快捷键支持
- 提供同步历史记录功能
- 支持多模块项目的 XML 自动查找
- 支持动态表名场景

## TODO List

### 高优先级
- [x] 支持按注解忽略字段（如 `@TableField`、`@Transient`）
- [x] 字段同步时保留已有字段的顺序合并新字段
- [x] 支持 `resultMap` 的字段映射同步
- [x] 支持 Mapper 和 XML 的互相跳转
- [x] 支持 SQL 代码提示与错误标红提示
- [x] 支持 mybatis sql 日志过滤预览功能
- [x] 集成 IDEA Database 工具增强类型映射和补全
- [ ] 支持从 XML 反向生成实体类字段
- [ ] 支持字段注释同步到 XML 注释

### 中优先级
- [x] 支持 `where` 条件片段同步
- [x] 增加批量插入语句模板支持
- [x] 支持自定义 JdbcType 映射配置
- [x] 提供同步预览功能（先预览再执行）
- [x] 支持 `<choose>/<when>/<otherwise>` 动态标签
- [x] 支持 MyBatis-Plus 注解（`@TableName`、`@TableId`、`@TableField`）
- [x] 支持生成常用 CRUD 模板
- [ ] 支持字段重命名时自动更新 XML

### 低优先级
- [x] 支持动态表名场景
- [x] 添加快捷键支持
- [x] 提供同步历史记录功能
- [x] 支持生成 Mapper 接口方法
- [ ] 支持多模块项目的 XML 自动查找
- [ ] 支持 XML 格式化配置
- [ ] 支持批量同步多个实体类
- [ ] 支持 TypeHandler 自定义类型映射
