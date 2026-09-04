package com.wuzhufolio.data.market

import com.wuzhufolio.domain.catalog.CoinDirectoryEntry
import com.wuzhufolio.domain.market.CmcMapCoin
import com.wuzhufolio.domain.market.MarketApiException
import com.wuzhufolio.domain.market.MarketCandle
import com.wuzhufolio.domain.market.MarketDataClient
import com.wuzhufolio.domain.market.MarketQuote
import com.wuzhufolio.domain.market.MarketRank
import com.wuzhufolio.domain.market.MarketRefreshError
import com.wuzhufolio.domain.market.PriceSource
import java.math.BigDecimal
import java.time.Instant

/**
 * 编排测试用假客户端：行为脚本化、逐调用计数（目录每日一次 / 单飞 / 兜底切换的断言基础）。
 */
internal class FakeMarketClient(
    val name: String,
) : MarketDataClient {

    var currentResult: (suspend (List<String>, List<String>, String?) -> List<MarketQuote>)? = null
    var cmcCurrentResult: (suspend (List<Long>, List<String>, String) ->
    List<com.wuzhufolio.domain.market.CmcQuote>)? = null
    var currentError: MarketRefreshError? = null
    var cmcError: MarketRefreshError? = null
    var historyResult: (suspend () -> List<MarketCandle>)? = { emptyList() }
    var directoryResult: List<CoinDirectoryEntry> = emptyList()
    var directoryError: MarketRefreshError? = null
    var directoryThrowable: Throwable? = null
    var rankResult: List<MarketRank> = emptyList()
    var cmcMapResult: List<CmcMapCoin> = emptyList()

    var currentCalls: Int = 0
    var cmcCurrentCalls: Int = 0
    var historyCalls: Int = 0
    var directoryCalls: Int = 0
    var rankCalls: Int = 0
    var cmcMapCalls: Int = 0
    var lastKey: String? = null

    override suspend fun fetchCurrent(coins: List<String>, fiats: List<String>, apiKey: String?):
    List<MarketQuote> {
        currentCalls++
        lastKey = apiKey
        currentError?.let { throw MarketApiException(it) }
        return currentResult?.invoke(coins, fiats, apiKey) ?: emptyList()
    }

    override suspend fun fetchCmcCurrent(
        cmcIds: List<Long>,
        fiats: List<String>,
        apiKey: String,
    ): List<com.wuzhufolio.domain.market.CmcQuote> {
        cmcCurrentCalls++
        lastKey = apiKey
        cmcError?.let { throw MarketApiException(it) }
        return cmcCurrentResult?.invoke(cmcIds, fiats, apiKey) ?: emptyList()
    }

    override suspend fun fetchHistory(
        coin: String,
        fiat: String,
        from: Instant,
        to: Instant,
        apiKey: String?,
    ): List<MarketCandle> {
        historyCalls++
        return historyResult?.invoke() ?: emptyList()
    }

    override suspend fun fetchDirectory(apiKey: String?): List<CoinDirectoryEntry> {
        directoryCalls++
        lastKey = apiKey
        directoryThrowable?.let { throw it }
        directoryError?.let { throw MarketApiException(it) }
        return directoryResult
    }

    override suspend fun fetchMarketRanking(page: Int, perPage: Int, apiKey: String?): List<MarketRank> {
        rankCalls++
        return rankResult
    }

    override suspend fun fetchCmcMap(apiKey: String, start: Long): List<CmcMapCoin> {
        cmcMapCalls++
        return cmcMapResult
    }
}

internal fun quoteOf(coin: String, fiat: String = "USD", price: String): MarketQuote =
    MarketQuote(coin, fiat, price.bd(), PriceSource.COINGECKO, Instant.parse("2026-01-15T10:00:00Z"))

internal fun cmcQuoteOf(cmcId: Long, fiat: String = "USD", price: String): com.wuzhufolio.domain.market.CmcQuote =
    com.wuzhufolio.domain.market.CmcQuote(cmcId, fiat, price.bd(), PriceSource.COINMARKETCAP,
    Instant.parse("2026-01-15T10:00:00Z"))

internal fun String.bd(): BigDecimal = BigDecimal(this)
