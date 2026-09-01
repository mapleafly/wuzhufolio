enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "wuzhufolio"

// M0 多模块骨架（task-breakdown T0.1）：app=组装入口/Compose 应用，ui=主题与组件库/主壳，data=存储/迁移/设置，domain=纯 Kotlin 领域
include("app", "domain", "data", "ui")
