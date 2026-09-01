rootProject.name = "wuzhufolio"

// M0 多模块骨架（task-breakdown T0.1）：app=组装入口/Compose 应用，ui=主题与组件库/主壳，data=存储/迁移/设置，domain=纯 Kotlin 领域
// 顺序 = 依赖方向（生产者在前、消费者在后）：:app 依赖 :ui/:data/:domain，若 :app 先于生产者配置，
// 其 Compose 插件在配置期对项目依赖的变体解析会命中「未配置的生产者 → No variants exist」（CI 复现）。
include("domain", "data", "ui", "app")
