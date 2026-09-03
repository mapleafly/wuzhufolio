package com.wuzhufolio.domain.catalog

/** 冻结映射/目录操作的目标币种在 coins 目录中不存在（调用方应先经检索取得合法 coin）。 */
class UnknownCoinException(coinId: Long) :
    RuntimeException("no coin with id " + coinId + " in the coin catalog")
