# 文档索引

本目录用于承接仓库的系统知识，避免将开发约束、架构细节、发布步骤和维护路线图堆在根目录 `AGENTS.md` 或 `README.md` 中。

## 架构

- [architecture/project-map.md](./architecture/project-map.md)：仓库顶层结构、关键入口文件、插件注册点
- [architecture/package-responsibilities.md](./architecture/package-responsibilities.md)：`src/main/java/com/eagga/mybatisfieldsync` 下各包职责

## 开发

- [development/local-dev.md](./development/local-dev.md)：本地开发环境、调试插件、打包命令
- [development/testing-and-verification.md](./development/testing-and-verification.md)：测试命令、Java 17 约束、验证口径
- [development/release-and-marketplace.md](./development/release-and-marketplace.md)：Marketplace 发布与 GitHub Actions 流程

## 产品

- [product/feature-matrix.md](./product/feature-matrix.md)：当前已实现能力与主要入口
- [product/usage-guide.md](./product/usage-guide.md)：详细使用说明与常见场景
- [product/sync-boundaries.md](./product/sync-boundaries.md)：字段同步支持边界、动态 SQL 范围、已知限制

## 维护

- [maintenance/roadmap.md](./maintenance/roadmap.md)：后续演进方向与 TODO 拆分
- [maintenance/known-constraints.md](./maintenance/known-constraints.md)：当前限制、运行噪音和协作注意事项
