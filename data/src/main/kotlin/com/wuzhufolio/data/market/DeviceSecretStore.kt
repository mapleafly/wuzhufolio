package com.wuzhufolio.data.market

import com.wuzhufolio.data.settings.SettingsRepository
import com.wuzhufolio.domain.security.AuthenticationFailedException
import com.wuzhufolio.domain.security.DeviceSecretCipher
import com.wuzhufolio.domain.security.Zeroization
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 应用级秘密存储（M5 · ADR-002 §2.1 方案甲落地）：行情平台 Key 经**设备密钥**（[deviceKey]，
 * 由引导层经 MasterKeyResolver 取用/首建，条目 device.key、降级文件 device.key）加密后存 settings 全局行；
 * 不进 .cpro 备份（恢复后重新配置，ADR-005 §3）。
 *
 * 解密失败（设备密钥更换/密文损坏）→ 记日志并视为未配置（UI 重新填写后覆盖），不静默损坏数据。
 */
class DeviceSecretStore(
    private val deviceKey: ByteArray,
    private val settings: SettingsRepository,
    private val logger: Logger = LoggerFactory.getLogger(DeviceSecretStore::class.java),
) : AutoCloseable {

    /** 是否有已存密文条目（无论能否解密——状态展示口径）。 */
    fun configured(key: String): Boolean = settings.getGlobal(key) != null

    /** 加密写入/覆盖（空串 = 清除——UI 层以 remove 语义调用）。 */
    fun put(key: String, purpose: String, plaintext: String) {
        settings.putGlobal(key, DeviceSecretCipher.seal(deviceKey, plaintext, purpose))
    }

    /** 解密读取；无条目或解密失败（错钥/损坏）→ null。 */
    @Suppress("SwallowedException") // 解密失败按「未配置（可覆盖重填）」处理并记日志——认证细节不外泄、不中断
    fun get(key: String, purpose: String): String? {
        val sealed = settings.getGlobal(key) ?: return null
        return try {
            DeviceSecretCipher.open(sealed, deviceKey, purpose)
        } catch (e: Exception) {
            logger.warn("device secret decrypt failed (key={}); treat as unconfigured", key)
            null
        }
    }

    /** 移除条目（恢复后在设置中重新配置）。 */
    fun remove(key: String) {
        settings.deleteGlobal(key)
    }

    override fun close() {
        Zeroization.wipe(deviceKey)
    }
}
