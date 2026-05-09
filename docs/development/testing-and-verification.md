# 测试与验证

## 推荐验证环境

在这个仓库里，最稳的本地验证方式是显式指定 Java 17，并使用可写的 Gradle Home：

```bash
export GRADLE_USER_HOME=/tmp/gradle-home
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

## 常用验证命令

### 完整单元测试

```bash
./gradlew --no-daemon clean test -x instrumentCode -x instrumentTestCode -x classpathIndexCleanup --console=plain
```

适用场景：

- 调整字段同步逻辑
- 修改 XML 渲染与格式策略
- 变更 TypeHandler/JdbcType 解析
- 调整补全与工具类行为

### 指定范围测试

```bash
./gradlew test --tests "com.eagga.mybatisfieldsync.completion.*" -x instrumentCode -x instrumentTestCode -x classpathIndexCleanup --console=plain
```

```bash
./gradlew test --tests "com.eagga.mybatisfieldsync.util.XmlFieldSyncSupportTest" -x instrumentCode -x instrumentTestCode -x classpathIndexCleanup --console=plain
```

### 编译检查

```bash
./gradlew compileJava --console=plain
```

### 插件校验

```bash
./gradlew verifyPlugin --console=plain
```

## 验证口径

- 没有真实命令输出，不要声称“已通过”。
- 如果只做了静态分析，要明确写“未验证”。
- 如果构建输出中出现环境噪音，要区分“噪音”和“真实失败”。

## 已知环境噪音

- `patchPluginXml` 阶段可能出现：

```text
Caught exception: Could not start the FSEvents stream: /Users/eagga/AIProject/mybatis-field-sync
```

这类信息在当前环境下通常不影响 `BUILD SUCCESSFUL` 结果，但需要和真正的 Gradle 失败区分开。
