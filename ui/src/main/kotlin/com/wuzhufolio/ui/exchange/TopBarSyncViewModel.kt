package com.wuzhufolio.ui.exchange

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.exchange.ExchangeSyncService
import com.wuzhufolio.domain.exchange.SyncStatus
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 顶栏手动同步（PRD 故事 4.3「提供手动同步按钮」+ ia.md §1 顶栏「手动同步交易按钮 + 同步中指示」；
 * 2026-09-08 走查补口）：任何页面常驻可达，同步全部已保存的交易所 API 密钥。
 *
 * 与 API 管理页的同步入口共用 ExchangeSyncService（单飞由服务内部 Mutex 保证）；
 * 无密钥 -> 引导提示；有密钥 -> 汇总「新增 N / 失败 M」。
 */
class TopBarSyncViewModel(private val service: ExchangeSyncService) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _toast = MutableStateFlow<WzToast?>(null)
    val toast: StateFlow<WzToast?> = _toast.asStateFlow()

    /** 手动同步全部密钥（按钮点击入口；进行中重复点击忽略）。 */
    fun syncNow() {
        if (_syncing.value) return
        _syncing.value = true
        scope.launch {
            try {
                val results = service.syncNow(null)
                _toast.value = if (results.isEmpty()) {
                    WzToast(WzToastKind.Failure, ApiCopy.SYNC_ALL_EMPTY)
                } else {
                    val newTrades = results.sumOf { it.newTrades }
                    val failed = results.count { it.status == SyncStatus.FAILED }
                    val message = if (failed > 0) {
                        ApiCopy.syncAllPartial(failedKeys = failed, newTrades = newTrades)
                    } else {
                        ApiCopy.syncAllDone(keys = results.size, newTrades = newTrades)
                    }
                    WzToast(if (failed > 0) WzToastKind.Failure else WzToastKind.Success, message)
                }
            } catch (t: Throwable) {
                _toast.value = WzToast(WzToastKind.Failure, t.message ?: ApiCopy.ERR_GENERIC)
            } finally {
                _syncing.value = false
            }
        }
    }

    fun dismissToast() {
        _toast.value = null
    }

    override fun onCleared() {
        scope.cancel()
    }

    /** 页面卸载显式释放（Compose remember VM 无宿主时调用）。 */
    fun dispose() {
        scope.cancel()
    }
}
