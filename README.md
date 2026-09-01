# WuZhuFolio

以隐私和安全为核心、数据完全本地化的加密资产组合追踪工具（桌面端）。

- **数据本地化**：交易记录、API 密钥、资金流水只存用户设备本地，零云端上传。
- **零遥测**：不内置任何遥测、统计上报或第三方分析 SDK。
- **加密**：SQLCipher 整库加密 + 分层密钥（DEK/KEK）+ AES-256-GCM + Argon2id（M1 起，ADR-002）。
- **开源**：AGPL-3.0（决策 D1），GitHub Releases 分发。

> 当前阶段：**P3 工程脚手架（M0）**。技术栈 Kotlin + Compose Desktop（ADR-001）。
> 需求基线见 `docs/prd/`，技术方案见 `docs/tech/`，流水线与状态看板见 `AGENTS.md` / `docs/dev/STATUS.md`。

## 环境要求

- JDK 17（推荐 mise 管理：`mise install`，见 `.mise.toml`）
- Gradle 无需安装：以仓库 Wrapper 为唯一真源（`./gradlew`）
- 本地开发环境搭建详见 `docs/tech/dev-setup.md`

## 构建 / 运行 / 测试

```bash
# 构建（编译 + 测试 + detekt）
./gradlew build

# 运行（GUI；数据目录默认 ~/.wuzhufolio，可用 WUZHUFOLIO_DATA_DIR 覆盖）
./gradlew :app:run

# 仅单元测试 / Compose UI 测试（离屏渲染，无需显示环境）
./gradlew test

# 静态检查
./gradlew detekt

# 打包冒烟（当前平台 app-image + uber jar）
./gradlew :app:createDistributable :app:packageUberJarForCurrentOS

# 原生安装包（当前平台：dmg / msi / deb，P7 签名公证）
./gradlew :app:packageDistributionForCurrentOS
```

## 仓库结构

```
app/      组装与入口（Compose 窗口、Koin DI、hello 链路引导）
ui/       Compose 主题（design-tokens 单源映射、明/暗双主题）、组件库、主壳导航骨架
data/     SQLite/Exposed 存储、schema 迁移框架（schema_version）、settings 读写、hello 链路
domain/   纯 Kotlin 领域（日志脱敏器、设置枚举；M4 计算引擎在此扩展）
docs/     PRD / 设计 / 技术方案 / 状态看板（见 AGENTS.md 目录约定）
```

## M0 已验证口径

- `./gradlew build` 绿：17 项测试全过（脱敏 / 迁移幂等 / hello 链路 / 双主题对比度 WCAG AA / 主壳 UI）。
- hello 链路：空界面启动 → 迁移建库 → 读设置 → 打一条脱敏日志（`~/.wuzhufolio/logs/wuzhufolio.log`）。
- UI 基座：侧边栏五页空壳 + 「组件走查（DEV）」页，顶栏 ☾/☀ 切换明/暗主题即时重渲染。
- 依赖许可证清单与 AGPL-3.0 兼容核验：`docs/tech/dependency-licenses.md`。

## 许可

AGPL-3.0，见 `LICENSE`。第三方依赖许可见 `docs/tech/dependency-licenses.md`。
