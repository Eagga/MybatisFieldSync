# 已知约束

## 协作边界

- 根目录可能存在证书、私钥和本地崩溃日志；除非明确要求，不应将这类文件纳入常规改动。
- 当前仓库已有任务外的工作树改动时，优先局部修改，不做无关回滚。

## 构建与环境

- Gradle JVM 至少 11，推荐直接使用 Java 17
- `build.gradle.kts` 固定使用 Java 17 toolchain
- Database 相关功能依赖 IntelliJ IDEA Ultimate

## 文档治理约定

- `AGENTS.md` 只做路由，不再堆积长篇仓库说明
- 架构、测试、发布、路线图应沉淀到 `docs/`
- README 以对外说明为主，内部维护信息尽量下沉到 `docs/`

## 功能限制

- 超复杂动态 SQL 仍建议先预览再执行
- `base_column_list` 识别仍依赖命名约定
- 更深层的 resultMap 高级结构同步仍未实现
