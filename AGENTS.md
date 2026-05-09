# 仓库协作入口

这个仓库是 `MyBatis Field Sync` IntelliJ 插件。此文件只做路由，不再堆积长篇说明。

## 阅读顺序

1. 先读 `docs/README.md`
2. 再按任务类型只打开相关文档，不要一次性把整个 `docs/` 全读入
3. 若任务涉及真实行为判断，最后回到代码与配置文件核实

## 先看这些文档

- `docs/README.md`：文档总入口
- `docs/architecture/project-map.md`：顶层结构与插件注册点
- `docs/architecture/package-responsibilities.md`：Java 包职责
- `docs/development/testing-and-verification.md`：推荐验证命令与测试口径
- `docs/development/release-and-marketplace.md`：签名、发布与 Marketplace 流程
- `docs/product/sync-boundaries.md`：字段同步边界与动态 SQL 支持范围

## 仓库关键信息

- 单模块 Gradle 插件项目
- Java toolchain 固定为 17
- IntelliJ 目标版本为 `IU 2023.3`
- Database 集成依赖 `com.intellij.database`
- 所有 UI 文案应放到 `src/main/resources/messages/`

## 真相源

- 插件行为与入口：`src/main/resources/META-INF/plugin.xml`
- 构建、Java 版本、IDE 目标版本：`build.gradle.kts`
- 发布与签名流程：`.github/workflows/publish-plugin.yml`
- 真实实现：`src/main/java/com/eagga/mybatisfieldsync/**`
- 测试依据：`src/test/java/com/eagga/mybatisfieldsync/**`

## 文档更新原则

- 更新文档时，优先从代码、配置和测试反推结论，不要互相抄旧 README 或旧 AGENTS。
- 如果某项能力未验证，只能写“已实现但未验证”或“未验证”，不能写成“已通过”。
- 当实现与文档不一致时，先修正文档，再决定是否补实现。

## 协作规则

- 改动保持聚焦，不要碰证书、私钥和崩溃日志，除非任务明确要求。
- 涉及 PSI/XML 写入，沿用现有 `WriteCommandAction.runWriteCommandAction(...)` 模式。
- 优先补 JUnit 5 的最小回归测试。
- 没有真实验证结果，不要声称“已完成/已通过”。受限环境下请按 `docs/development/testing-and-verification.md` 中的 Java 17 与 `GRADLE_USER_HOME` 方案执行。
- 提交信息优先遵循 Conventional Commit：`feat`、`fix`、`docs`、`test`、`refactor`、`chore`。

## 常用命令

- `./gradlew runIde`
- `./gradlew buildPlugin`
- `./gradlew compileJava --console=plain`
- `./gradlew verifyPlugin --console=plain`
