package com.wuzhufolio.ui.settings

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.domain.settings.GeneralSettingsView
import com.wuzhufolio.domain.settings.PrecisionPreset
import java.math.BigDecimal
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import com.wuzhufolio.ui.i18n.settingsStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 通用设置 UI 状态（视图 + 写入中标记 + toast；主题/盈亏配色由 ShellViewModel 持有，见 SettingsPage）。 */
data class GeneralSettingsUiState(
    val view: GeneralSettingsView = GeneralSettingsView(),
    val cashInput: String = "",
    /** 小额阈值输入框（首次加载回填当前值；保存成功后归一为规范串——避免无关写入打断编辑）。 */
    val thresholdInput: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val toast: WzToast? = null,
)

/**
 * 通用设置 VM（M10 · T10.1）：设置项读取 + 单项写入（写后 reload 视图；「切换即时生效」由消费点
 * 运行期读取保证——法币折算每次取设置，白名单经注入 provider）。
 */
@Suppress("TooManyFunctions") // 每个设置项一个写入口（法币/精度/枚举/白名单增删/阈值/代理）+ 加载/toast，动作面固有
class GeneralSettingsViewModel(
    private val service: GeneralSettingsService,
    /**
     * M11 T11.3：代理开关运行期生效钩子（写入成功后同步 ProxyRuntime 检测态与状态栏指示）。
     * 默认空实现 = 仅落设置（既有 UI 测试无需构造引导层运行时）。
     */
    private val onProxyEnabledChange: (Boolean) -> Unit = {},
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(GeneralSettingsUiState())
    val state: StateFlow<GeneralSettingsUiState> = _state.asStateFlow()

    /**
     * 写入串行化锁（M12 统一：**M11 §6 遗留闭环**）。
     *
     * 原实现为「busy 中直接 return」防重入，会**静默丢弃**用户在写入未完成时的第二次操作
     * （连续改两项设置即触发，表现为「点了没反应」）。M11 的 `DesktopSettingsViewModel` 已改排队串行，
     * 本模块同步统一：busy 仅用于 UI 置忙，动作一律排队执行，不吞。
     */
    private val writeLock = Mutex()

    /** 阈值输入框是否已用存储值回填（仅首次；后续 reload 不打断编辑中）。 */
    private var thresholdInitialized = false

    init {
        reload()
    }

    fun reload() {
        scope.launch {
            runCatching { service.view() }
                .onSuccess { view ->
                    _state.update {
                        it.copy(
                            view = view,
                            error = null,
                            thresholdInput = if (thresholdInitialized) {
                                it.thresholdInput
                            } else {
                                view.smallThreshold.toPlainString()
                            },
                        )
                    }
                    thresholdInitialized = true
                }
                .onFailure { t -> _state.update { it.copy(error = t.message ?: settingsStrings.loadFailed) } }
        }
    }

    fun onCashInput(value: String) = _state.update { it.copy(cashInput = value, error = null) }

    fun setBaseFiat(code: String) = launch({ service.setBaseFiat(code) }, settingsStrings.baseFiatSwitched(code))

    fun setPrecision(preset: PrecisionPreset) = launch({ service.setPrecision(preset) }, settingsStrings.precisionSaved)

    fun setUsernameEnum(on: Boolean) = launch(
        { service.setUsernameEnum(on) },
        settingsStrings.usernameEnumSet(on),
    )

    fun addCashCoin() {
        val input = _state.value.cashInput.trim()
        if (input.isEmpty()) {
            _state.update { it.copy(error = SettingsCopy.CASH_INPUT_LABEL) }
            return
        }
        launch({ service.addCashCoin(input) }, settingsStrings.cashCoinAdded(input.lowercase()))
            .also { _state.update { s -> s.copy(cashInput = "") } }
    }

    fun removeCashCoin(cgId: String) = launch({ service.removeCashCoin(cgId) }, settingsStrings.cashCoinRemoved(cgId))

    fun onThresholdInput(value: String) = _state.update { it.copy(thresholdInput = value, error = null) }

    /** 保存小额阈值（自由数值 ≥ 0；0 = 不启用——M10 走查反馈修复轮：预设档废弃，按用户规模自定）。 */
    fun saveSmallAmountThreshold() {
        val parsed = _state.value.thresholdInput.trim().toBigDecimalOrNull()
        if (parsed == null || parsed.signum() < 0) {
            _state.update { it.copy(error = SettingsCopy.THRESHOLD_INVALID) }
            return
        }
        val canonical = parsed.stripTrailingZeros().toPlainString()
        _state.update { it.copy(thresholdInput = canonical) }
        launch({ service.setSmallAmountThreshold(parsed) }, settingsStrings.thresholdSaved(canonical))
    }

    fun setProxyEnabled(on: Boolean) = launch(
        {
            service.setProxyEnabled(on)
            // 落盘后即时切换请求走向（PRD 6.2：关 = 直连 / 开 = 自动检测系统代理），无需重启
            onProxyEnabledChange(on)
        },
        settingsStrings.proxySet(on),
    )

    fun dismissToast() = _state.update { it.copy(toast = null) }

    private fun launch(block: suspend () -> Unit, successMessage: String) {
        scope.launch {
            writeLock.withLock {
                _state.update { it.copy(busy = true, error = null) }
                runCatching { block() }
                    .onSuccess {
                        reload()
                        _state.update { it.copy(toast = WzToast(WzToastKind.Success, successMessage)) }
                    }
                    .onFailure { t ->
                        _state.update {
                            it.copy(
                                error = t.message ?: settingsStrings.saveFailed,
                                toast = WzToast(WzToastKind.Failure, t.message ?: settingsStrings.saveFailed),
                            )
                        }
                    }
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    fun dispose() {
        scope.cancel()
    }
}
