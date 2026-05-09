# 使用指南

## 基础使用

### 数据库连接配置（可选，增强功能）

1. 打开 IDEA 的 Database 工具窗口（`View -> Tool Windows -> Database`）
2. 添加数据源连接（支持 MySQL、PostgreSQL、Oracle、SQL Server 等）
3. 测试连接成功后，插件会自动读取表结构信息
4. 字段同步时会使用数据库列类型进行更准确的 JdbcType 映射
5. 此功能需要 IntelliJ IDEA Ultimate

### 字段同步

1. 打开 Java 实体类文件
2. 在类名处或空白处右键，选择 `MyBatis Field Sync -> Sync Fields to XML`
3. 勾选需要同步的字段
4. 选择目标 XML 与 Statement
5. 可先预览，再确认写入

### 批量同步向导

1. 打开任意一个 Java 实体类文件
2. 在编辑器类名处或空白处右键，选择 `MyBatis Field Sync -> Batch Sync Wizard`
3. 在首个向导对话框中勾选多个实体类
4. 插件会按所选顺序逐项打开原有的字段/Statement 选择与预览界面
5. 某一项失败或手动跳过时，向导会继续处理后续实体
6. 全部处理结束后，会弹出汇总报告

### 从 XML 反向生成字段

1. 打开 Mapper XML 文件
2. 将光标放在 `resultMap` 或 `base_column_list` 内，或直接选中其中一段 XML 文本
3. 右键选择 `MyBatis Field Sync -> Generate Fields From XML`
4. 插件会优先使用 `resultMap type/javaType/ofType` 推断实体类；若当前片段来自 `base_column_list`，则尝试根据 `mapper namespace` 推断实体
5. 预览字段草稿与冲突项，确认后写入实体类

### 字段注释同步到 XML

1. 打开 Java 实体类文件
2. 右键选择 `MyBatis Field Sync -> Sync Field Comments to XML`
3. 选择目标 XML、目标 Statement 与字段
4. 预览 XML 注释变更，确认后执行
5. 若字段已有同缩进注释，会就地替换；没有则自动插入注释行

### 字段重命名联动

1. 在 Java 实体字段上执行 IDEA Rename
2. 插件会自动更新对应 Mapper XML 中的：
   - `#{field}`
   - `${field}`
   - `<if test="field != null">`
   - `<result property="field">`
3. 所有改动可直接使用 IDE Undo / Redo 回退或重做

### CRUD 模板生成

1. 打开 Java 实体类文件
2. 在编辑器类名处或空白处右键，选择 `MyBatis Field Sync -> Generate CRUD Template`，或按 `Ctrl+Alt+G`
3. 选择要生成的模板（ResultMap、Insert、Update、Delete、Select）
4. 自动在对应的 Mapper XML 中生成标准 CRUD 语句，支持动态表名 `${tableName}`

### 快捷键

- 字段同步：`Ctrl+Alt+S`
- CRUD 生成：`Ctrl+Alt+G`
- 查看历史：`Ctrl+Alt+H`

### 同步历史记录

1. 右键选择 `MyBatis Field Sync -> View Sync History` 或按 `Ctrl+Alt+H`
2. 查看同步时间、实体类、XML 文件、Statement ID 和字段列表
3. 可点击 `Clear History` 清空记录

### JPA 风格方法名补全

1. 在 Mapper 接口中可用两种方式触发：
   - 骨架模式：先写返回值，再输入方法名前缀，例如 `List<Activity> findBy`
   - 裸输入模式：直接输入 `findBy` / `countBy` / `deleteBy` / `existsBy`
2. 若未自动弹出，请手动按 `Ctrl+Space` 触发补全
3. 选择需要的方法名，如 `findByNameAndAge`
4. 自动生成方法签名和对应 XML SQL

实体类解析规则按优先级如下：

1. 优先从 Mapper 父接口泛型解析，例如 `BaseMapper<User>`
2. 若无泛型信息，则按接口名推断：`UserMapper -> User`
3. 按实体短类名在项目内搜索并匹配

### MyBatis SQL 日志过滤预览

1. 在 IDEA 右侧工具栏打开 `MyBatis SQL Preview`
2. 勾选 `Enable SQL Log Filter`
3. 运行本地项目后，面板会从运行日志中过滤 `Preparing` / `Parameters` 并展示可读 SQL
4. 点击 `Clear` 可清空预览文本框
5. 取消勾选后停止采集

## 常见场景

### 场景 1：新增字段到已有 INSERT 语句

```java
public class User {
    private Long id;
    private String name;
    private String email;
    private Integer age;
}
```

```xml
<insert id="insert">
    INSERT INTO user (id, name, email, age)
    VALUES (#{id}, #{name}, #{email}, #{age})
</insert>
```

### 场景 2：动态 UPDATE 语句增量补齐

```xml
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

### 场景 3：ResultMap 字段映射同步

```xml
<resultMap id="BaseResultMap" type="User">
    <result column="id" property="id" jdbcType="BIGINT"/>
    <result column="name" property="name" jdbcType="VARCHAR"/>
    <result column="email" property="email" jdbcType="VARCHAR"/>
    <result column="age" property="age" jdbcType="INTEGER"/>
</resultMap>
```

### 场景 4：数据库集成增强类型映射

```xml
<insert id="insert">
    INSERT INTO user (id, name, create_time)
    VALUES (#{id,jdbcType=BIGINT}, #{name,jdbcType=VARCHAR}, #{createTime,jdbcType=TIMESTAMP})
</insert>
```

### 场景 5：一键生成 CRUD 模板

```xml
<insert id="insert">
    INSERT INTO ${tableName} (id, name, email, age)
    VALUES (#{id,jdbcType=BIGINT}, #{name,jdbcType=VARCHAR}, #{email,jdbcType=VARCHAR}, #{age,jdbcType=INTEGER})
</insert>
```

### 场景 6：JPA 风格方法名自动补全

```java
List<User> findByNameAndAge(@Param("name") String name, @Param("age") Integer age);
```

```xml
<select id="findByNameAndAge" resultMap="BaseResultMap">
    SELECT * FROM user WHERE name = #{name} AND age = #{age}
</select>
```

支持示例：

- `findByAgeGreaterThan`
- `countByNameLike`
- `deleteByIdIn`
- `findByCreatedAtBetween`
- `existsByDeletedAtIsNull`
- `findByNameOrEmail`
