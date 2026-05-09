# 功能矩阵

## 核心同步

- Java 实体字段同步到 MyBatis XML
  - 入口：`action.SyncFieldsAction`
  - 核心：`service.FieldSyncService`
- 批量同步向导
  - 入口：`action.BatchSyncWizardAction`
  - 工作流：`action.SyncFieldsWorkflow`
- XML 反向生成实体字段
  - 入口：`action.GenerateFieldsFromXmlAction`
  - 辅助：`util.XmlFieldSyncSupport`

## 智能生成

- CRUD 模板生成
  - 入口：`action.GenerateCrudAction`
  - 核心：`service.CrudTemplateService`
- Mapper 方法生成
  - 入口：`action.GenerateMapperMethodsAction`
  - 核心：`service.MapperInterfaceService`
- JPA 风格方法名补全
  - 注册：`completion.MapperMethodCompletionContributor`
  - 解析：`completion.MethodNameParser`
  - SQL 生成：`completion.SqlGenerator`、`completion.XmlStatementGenerator`

## 开发辅助

- Java/XML 双向导航
  - `marker.MapperLineMarkerProvider`
  - `marker.XmlLineMarkerProvider`
- 字段重命名联动 XML
  - `refactor.MyBatisFieldRenameListenerProvider`
- Java 字段注释同步到 XML 注释
  - `action.SyncFieldCommentsToXmlAction`
- SQL 日志过滤预览工具窗
  - `toolwindow.SqlLogPreviewToolWindowFactory`
  - `service.SqlLogPreviewService`

## 配置与增强

- Database Tools 集成增强类型映射
  - `database.DatabaseConnectionService`
  - `database.DatabaseFieldEnhancer`
  - `database.DatabaseTypeMapper`
- 设置页
  - `settings.MyBatisFieldSyncConfigurable`
  - `settings.MyBatisFieldSyncSettings`
- 自定义 JdbcType / TypeHandler / XML 格式策略
  - `util.JdbcTypeUtil`
  - `util.TypeMappingUtil`
  - `util.XmlFormatSettingsUtil`
  - `util.XmlMappingRenderUtil`
