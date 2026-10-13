# WuZhuFolio

以隐私和安全为核心、数据完全本地化的加密资产组合追踪工具（桌面端）。

- **数据本地化**：交易记录、API 密钥、资金流水只存用户设备本地，零云端上传。
- **零遥测**：不内置任何遥测、统计上报或第三方分析 SDK。
- **加密**：SQLCipher 整库加密 + 分层密钥（DEK/KEK）+ AES-256-GCM + Argon2id（M1 起，ADR-002）。
- **开源**：AGPL-3.0（决策 D1），GitHub Releases 分发。

> 当前阶段：**P8 迭代 —— v0.1.0 / v0.1.1 / v0.2.0 均已发布**（v0.2.0 = 里程碑 M14：Material 3 界面体系统一 + 使用反馈收口，2026-09-30）；
> **下一迭代 = 桌面端体验深化（人工 2026-10-10 拍板）**。
> 技术栈 Kotlin + Compose Desktop（ADR-001）。
> 需求基线见 `docs/prd/`，技术方案见 `docs/tech/`，流水线与状态看板见 `AGENTS.md` / `docs/dev/STATUS.md`。

## 环境要求

- **JDK 17 或 21**（任一即可；发布/CI 基线为 Temurin 17，自编译产物请自行核对）
  - 用 mise 管理 JDK 是**可选建议**（`mise install`，见 `.mise.toml`），直接用系统/发行版 JDK 亦可
- Gradle 无需安装：以仓库 Wrapper 为唯一真源（`./gradlew`，8.14.4）
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

# 原生安装包（当前平台：dmg / msi / deb）
./gradlew :app:packageDistributionForCurrentOS
```

> **macOS 用户**：本项目**不发布 macOS 发行包**（商店外分发需要 Developer ID 签名与 Apple 公证，当前不采购证书）。
> 需要 macOS 版请自行编译：`brew install --cask temurin@17` → `git clone` → `./gradlew :app:packageDmg`；
> 自编译产物未签名/未公证，首次打开需**右键 → 「打开」**放行。完整步骤见 `docs/release/user-guide.md` §3 ②。

## 仓库结构

```
app/      组装与入口（Compose 窗口、Koin DI、hello 链路引导）
ui/       Compose 主题（design-tokens 单源映射、明/暗双主题）、组件库、主壳导航骨架
data/     SQLite/Exposed 存储、schema 迁移框架（schema_version）、settings 读写、hello 链路
domain/   纯 Kotlin 领域（日志脱敏器、设置枚举；M4 计算引擎在此扩展）
docs/     PRD / 设计 / 技术方案 / 状态看板（见 AGENTS.md 目录约定）
```

## 质量与验证口径

- 全量回归（`./gradlew build detekt`）：**820 用例 / 816 执行 / 0 失败 / 0 错误 / 4 跳过**
  （app 59 · data 312 · domain 227 · ui 222；4 条 = env 门控 live 网络冒烟）+ **detekt 0**（`maxIssues=0`）+ 编译警告 0
  （口径随每次改动回填，以 `build/test-results` 聚合实测为准；见 `AGENTS.md §3.1` 收尾轮状态对齐 checklist）。
- 视觉回归（4 页面 × 2 主题 × 3 尺寸 = **24 张**）随 `./gradlew test` 渲染到 `ui/build/visual-regression/`，
  并与 `docs/design/baseline/` 做**基线比对（截图 diff，报告型不阻断）**：`./gradlew :ui:test --tests "*visual regression*"`
  → 见 `ui/build/visual-regression/diff-*.md`（CI 已归档，见 `docs/dev/decisions/D47-B5工程项（CI门禁与规约固化）.md §2.1`）。
- **影片素材写回默认关闭**（全量测试不改写仓库）：需要更新 `docs/release/promo/assets/ui/` 时用
  `WUZHUFOLIO_FILM_ASSETS=1 ./gradlew :ui:test --tests "*film asset*"`（或 `-Pwuzhufolio.filmAssets=true`）；
  出片链路 `docs/release/promo/tools/render-0.2.0.sh` 已自动开启（见 `docs/release/promo/README.md`）。
- 硬约束结构守护：`data` 模块 `SecurityGuardTest`（出站主机白名单 / 禁明文 http / 禁遥测依赖 / 禁网络日志 appender / 数据目录单源）。
- hello 链路：空界面启动 → 迁移建库 → 读设置 → 打一条脱敏日志（`~/.wuzhufolio/logs/wuzhufolio.log`）。
- 当前状态与阶段门以 `docs/dev/STATUS.md` 为唯一真源；独立复跑记录见 `docs/dev/项目全面评审报告.md`（2026-10-10）。
- 依赖许可证清单与 AGPL-3.0 兼容核验：`docs/tech/dependency-licenses.md`。

## 许可

AGPL-3.0，见 `LICENSE`。第三方依赖许可见 `docs/tech/dependency-licenses.md`。
