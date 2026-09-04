package com.wuzhufolio.domain.exchange

/**
 * 交易所同步错误模型（T6.1 · ADR-004 §2「错误映射」/ PRD 统一异常处理 B2/N2）。
 *
 * Binance 业务码 → 类型化错误（api-contracts §2.1）：
 * - -2015/-2014（密钥失效/格式错误）→ [ExchangeError.InvalidKey]（UI 文案 B2「Binance API 密钥已失效，请检查或更新」）；
 * - -1003/-1004/HTTP 429（限流）/418（IP 封禁）→ [ExchangeError.RateLimited]；
 * - -1021（时间戳偏差）→ [ExchangeError.TimestampSkew]（适配器内部取服务器时间重试一次后仍失败才上抛）；
 * - -1022（签名错误）→ [ExchangeError.SignatureInvalid]（提示密钥错误）；
 * - 网络不可达/超时 → [ExchangeError.Network]（PRD N2 保留本地账本语义）；
 * - 其余 HTTP → [ExchangeError.Http(code)]；
 * - 本地/内部（载荷解析失败、DB 异常等）→ [ExchangeError.Internal(detail)]（detail 仅入日志，不上 UI）。
 */
sealed interface ExchangeError {

    /** 密钥失效/格式错误（B2：需要用户检查或更新密钥）。 */
    data object InvalidKey : ExchangeError

    /** 签名错误（密钥不匹配导致签名无法验证；同 B2 提示「密钥错误」家族）。 */
    data object SignatureInvalid : ExchangeError

    /** 限流/封禁（429/-1003/-1004/418）。 */
    data object RateLimited : ExchangeError

    /** 时间戳偏差（-1021；适配器层自动对时重试一次，仍失败才上抛）。 */
    data object TimestampSkew : ExchangeError

    /** 网络不可达/超时（PRD N2：保留上次成功数据 + 时间戳语义）。 */
    data object Network : ExchangeError

    /** 其他 HTTP 错误（带状态码）。 */
    data class Http(val code: Int) : ExchangeError

    /** 内部错误（本地解析/数据库等；detail 仅入日志，不进 UI/日志 message）。 */
    data class Internal(val detail: String) : ExchangeError
}

/** 平台级同步异常（数据层 HTTP 客户端内部抛用；用例层以结果类型呈现，见 [ExchangeSyncOutcome]）。 */
class ExchangeApiException(val kind: ExchangeError) : RuntimeException(kind.toString())

/** 同名 API 密钥已存在（同一账户同一交易所内别名唯一，data-model §2.2 UNIQUE 约束）。 */
class DuplicateApiKeyNameException(val name: String) : RuntimeException("duplicate api key name: " + name)
