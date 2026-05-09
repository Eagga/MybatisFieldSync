# 本地开发

## 环境要求

- JDK 17
- IntelliJ IDEA 2023.3+（调试 Database 相关能力时建议 Ultimate）
- Gradle Wrapper（仓库自带）

`build.gradle.kts` 当前约束：

- Gradle JVM 至少 11
- Java toolchain 固定为 17
- IntelliJ Platform 目标版本 `2023.3`
- 插件类型 `IU`

## 常用命令

在仓库根目录执行：

```bash
./gradlew runIde
```

- 启动沙箱 IDEA，验证插件行为

```bash
./gradlew buildPlugin
```

- 打包安装 ZIP，产物位于 `build/distributions/`

```bash
./gradlew compileJava --console=plain
```

- 做最小编译检查

## 本地调试建议

- 先在 `runIde` 沙箱中安装当前构建出的插件，不要混用旧 ZIP。
- 若验证 Java 补全、Gutter、重构监听，优先在最小 demo 工程中复现。
- 涉及 SQL 预览或 Database 集成时，先确认 IDE 已启用相应功能，再判断是不是插件问题。
