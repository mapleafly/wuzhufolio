package com.wuzhufolio.domain.security

import java.security.SecureRandom

/**
 * 加密服务门面（architecture §2.3「CryptoService：Argon2id、DEK/KEK 包解包、字段级加解密」，ADR-002）。
 *
 * 消费方：M2（账户创建/登录/改密/记住我）、M6（api_keys 凭证列落库）、M9（.cpro 解密→目标账户 DEK 重加密）。
 * 本类为纯计算（无 IO、不持有任何密钥）；密钥材料生命周期与擦除归调用方（[Zeroization]）。
 */
class CryptoService(private val defaultParams: KdfParams = KdfParams.DEFAULT) {

    /** 新账户随机盐（16 字节，存 accounts.kdf_salt）。 */
    fun newSalt(): ByteArray = randomBytes(Argon2Kdf.SALT_BYTES)

    /** 新账户随机 DEK（32 字节）。 */
    fun newDek(): ByteArray = randomBytes(32)

    /** 密码派生 KEK（按冻结默认参数——新建账户一律用默认，ADR-002 §2）。 */
    fun deriveKek(password: CharArray, salt: ByteArray): ByteArray =
        Argon2Kdf.deriveKek(password, salt, defaultParams)

    /** 按账户存储参数派生（登录/改密/导入历史账户用；参数来自 accounts.kdf_params）。 */
    fun deriveKek(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray =
        Argon2Kdf.deriveKek(password, salt, params)

    /** 用账户 KEK 包裹 DEK（存 accounts.wrapped_dek）。 */
    fun wrapDek(dek: ByteArray, kek: ByteArray, accountId: String): String =
        KeyWrap.wrap(dek, kek, accountId)

    /** 解包 DEK；KEK 错误 → [AuthenticationFailedException]（= 密码错误语义）。 */
    fun unwrapDek(wrapped: String, kek: ByteArray, accountId: String): ByteArray =
        KeyWrap.unwrap(wrapped, kek, accountId)

    /** 凭证字段加密（api_keys 四列，AAD 绑定 账户/条目/列）。 */
    fun encryptField(plain: String, dek: ByteArray, accountId: String, apiKeyId: String, column: String): String =
        FieldCipher.encrypt(plain, dek, accountId, apiKeyId, column)

    /** 凭证字段解密（认证失败 → [AuthenticationFailedException]）。 */
    fun decryptField(encoded: String, dek: ByteArray, accountId: String, apiKeyId: String, column: String): String =
        FieldCipher.decrypt(encoded, dek, accountId, apiKeyId, column)

    /** 使用后擦除密钥材料（ADR-002 §2「密钥擦除」）。 */
    fun wipe(vararg keys: ByteArray) {
        Zeroization.wipe(*keys)
    }

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }
}
