# 项目地图

## 顶层结构

```text
mybatis-field-sync/
├── AGENTS.md
├── README.md
├── LICENSE
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
├── .github/workflows/
├── docs/
└── src/
```

## 关键入口文件

- `build.gradle.kts`
  - Gradle Kotlin DSL 构建定义
  - Java toolchain 17
  - IntelliJ Platform 插件目标为 `IU 2023.3`
- `src/main/resources/META-INF/plugin.xml`
  - 插件 ID、动作菜单、Gutter、补全、重构监听、工具窗注册
- `.github/workflows/publish-plugin.yml`
  - GitHub Actions 发布链路：`clean -> verifyPlugin -> signPlugin -> publishPlugin`
- `src/main/resources/messages/`
  - 中英文资源包；所有 UI 文案应优先从这里取值

## 源码目录

- `src/main/java/com/eagga/mybatisfieldsync`
  - 插件生产代码
- `src/main/resources`
  - `plugin.xml`、图标、国际化资源、可选 Database 依赖声明
- `src/test/java/com/eagga/mybatisfieldsync`
  - JUnit 5 单元测试

## 注册点速查

基于 `src/main/resources/META-INF/plugin.xml`：

- 设置页：`settings.MyBatisFieldSyncConfigurable`
- Java/XML Gutter：`marker.MapperLineMarkerProvider`、`marker.XmlLineMarkerProvider`
- Java 补全：`completion.MapperMethodCompletionContributor`
- 字段重命名联动：`refactor.MyBatisFieldRenameListenerProvider`
- SQL 预览工具窗：`toolwindow.SqlLogPreviewToolWindowFactory`
- 编辑器右键动作：
  - `action.SyncFieldsAction`
  - `action.BatchSyncWizardAction`
  - `action.GenerateCrudAction`
  - `action.GenerateMapperMethodsAction`
  - `action.SyncFieldCommentsToXmlAction`
  - `action.GenerateFieldsFromXmlAction`
  - `action.ViewSyncHistoryAction`
