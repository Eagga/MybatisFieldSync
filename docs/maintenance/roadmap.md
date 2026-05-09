# 路线图

## 已完成的关键能力

- 按注解忽略字段
- `resultMap` / `where` / `base_column_list` / `insert` / `update` 同步
- 多层动态 SQL 深层结构同步
- Java/XML 双向导航
- SQL 日志过滤预览
- CRUD 模板生成
- Mapper 方法生成
- XML 格式策略配置
- TypeHandler 自定义映射
- 批量同步向导
- XML 反向生成实体字段

## 高优先级待办

- resultMap 高级结构同步（`id/constructor/association/collection/discriminator`）
- 同步时清理已失效字段，并支持预览
- XML/Mapper 一致性检查与一键修复
- Inspection / Intention Action / Quick Fix

## 中优先级待办

- 缺失 Mapper/XML 的一键初始化生成
- 自定义 CRUD / XML 模板
- 更多 MyBatis-Plus 注解语义支持

## 低优先级与持续优化

- 多模块 XML 查找策略继续增强
- 批量同步流程继续打磨
- 文档与实现持续对齐，避免 README/TODO 漂移
