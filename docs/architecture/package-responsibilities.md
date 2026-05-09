# 包职责说明

## 生产代码包

- `action`
  - 编辑器菜单动作和工作流入口
  - 负责上下文识别、弹窗调起、调用服务层
- `service`
  - 核心业务逻辑
  - 包含字段同步、CRUD 生成、Mapper 方法生成、同步历史、SQL 预览
- `completion`
  - JPA 风格方法名解析、补全项插入、XML 语句生成
- `database`
  - Database Tools 集成与数据库列类型增强
- `marker`
  - Java/XML 双向导航的 Gutter 逻辑
- `refactor`
  - IDE Rename 链路下的 XML 引用联动更新
- `settings`
  - 插件设置状态与设置页 UI
- `toolwindow`
  - 右侧 `MyBatis SQL Preview` 工具窗
- `ui`
  - Swing 对话框、表格模型、预览与汇总视图
- `util`
  - 名称转换、字段过滤、JdbcType/TypeHandler 映射、XML 渲染与格式策略等通用能力
- `model`
  - 领域模型与结果对象
- `i18n`
  - 资源包访问入口
- `injector`
  - SQL 语言注入

## 测试代码包

- `src/test/java/.../completion`
  - 方法名解析和 SQL 生成测试
- `src/test/java/.../service`
  - 同步结构识别、字段同步、SQL 日志预览测试
- `src/test/java/.../util`
  - XML 渲染、格式策略、TypeHandler/JdbcType 映射测试

## 当前建议

- 新增功能时，优先先判断它属于“动作入口”“服务逻辑”“渲染工具”“设置状态”哪一层，再落包。
- 若某个文件开始同时承担“解析 + 写入 + UI 反馈”，优先拆出 util 或 service 辅助类。
