package com.wuzhufolio.ui.backup

/**
 * 备份/导出文件名助手（M9 走查反馈修复轮 · 2026-09-10）：
 * 原生保存对话框不强制扩展名——用户只输入文件名时自动补全默认扩展名
 * （.cpro 备份 / .csv 明文导出）；**恢复侧不做扩展名校验**（.cpro 由明文头部识别，
 * 任何文件名形态均可导入——格式真源 = 头部 JSON，非扩展名）。
 */
object BackupFileNames {

    /** 已带目标扩展名（任意大小写）则原样返回，否则补全。 */
    fun withExtension(path: String, extension: String): String {
        val suffix = if (extension.startsWith(".")) extension else "." + extension
        return if (path.endsWith(suffix, ignoreCase = true)) path else path + suffix
    }
}
