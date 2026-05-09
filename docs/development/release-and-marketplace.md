# 发布与 Marketplace

## GitHub Actions 工作流

当前仓库使用：

- `.github/workflows/publish-plugin.yml`

触发方式：

- `workflow_dispatch`
- 输入参数：
  - `version`
  - `channel`

## 必需 Secrets

根据工作流定义，仓库需要配置：

- `PUBLISH_TOKEN`
- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

## 发布链路

工作流执行顺序：

```text
clean
-> verifyPlugin
-> signPlugin
-> publishPlugin
-> Create GitHub Release
```

## 本地可选命令

```bash
./gradlew verifyPlugin --console=plain
```

```bash
PLUGIN_VERSION=1.0.1 PUBLISH_CHANNEL=default ./gradlew signPlugin publishPlugin
```

说明：

- 本地发布命令依赖环境变量或 Gradle properties 中的发布凭据
- CI 已对缺失的 Secrets 做显式校验
- 发布版本号优先读取 `PLUGIN_VERSION`，否则回退到 `pluginVersion` 属性，再回退到默认值 `1.0.0`
