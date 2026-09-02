package com.wuzhufolio.domain.security

/**
 * 认证失败：GCM tag 校验不通过——密钥错误、AAD 不符或密文被篡改。
 * M2 登录语义：KEK 解包 wrapped_dek 失败即「密码错误」（ADR-002 §3：GCM tag 校验即完成密码认证）。
 */
class AuthenticationFailedException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
