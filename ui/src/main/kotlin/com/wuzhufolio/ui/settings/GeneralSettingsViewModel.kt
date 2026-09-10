package com.wuzhufolio.ui.settings

import androidx.lifecycle.ViewModel
import com.wuzhufolio.domain.settings.GeneralSettingsService
import com.wuzhufolio.domain.settings.GeneralSettingsView
import com.wuzhufolio.domain.settings.PrecisionPreset
import java.math.BigDecimal
import com.wuzhufolio.ui.components.WzToast
import com.wuzhufolio.ui.components.WzToastKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(GeneralSettingsUiState())
    val state: StateFlow<GeneralSettingsUiState> = _state.asStateFlow()

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
                .onFailure { t -> _state.update { it.copy(error = t.message ?: "读取设置失败") } }
        }
    }

    fun onCashInput(value: String) = _state.update { it.copy(cashInput = value, error = null) }

    fun setBaseFiat(code: String) = launch({ service.setBaseFiat(code) }, "基础法币已切换为 " + code)

    fun setPrecision(preset: PrecisionPreset) = launch({ service.setPrecision(preset) }, "默认精度已保存")

    fun setUsernameEnum(on: Boolean) = launch(
        { service.setUsernameEnum(on) },
        if (on) "登录页用户名枚举已开启" else "登录页用户名枚举已关闭",
    )

    fun addCashCoin() {
        val input = _state.value.cashInput.trim()
        if (input.isEmpty()) {
            _state.update { it.copy(error = SettingsCopy.CASH_INPUT_LABEL) }
            return
        }
        launch({ service.addCashCoin(input) }, "已加入稳定币白名单：" + input.lowercase())
            .also { _state.update { s -> s.copy(cashInput = "") } }
    }

    fun removeCashCoin(cgId: String) = launch({ service.removeCashCoin(cgId) }, "已移除：" + cgId)

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
        launch({ service.setSmallAmountThreshold(parsed) }, "小额阈值已保存（" + canonical + "）")
    }

    fun setProxyEnabled(on: Boolean) = launch(
        { service.setProxyEnabled(on) },
        if (on) "系统代理已开启（自动检测）" else "系统代理已关闭（直连）",
    )

    fun dismissToast() = _state.update { it.copy(toast = null) }

    private fun launch(block: suspend () -> Unit, successMessage: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            runCatching { block() }
                .onSuccess {
                    reload()
                    _state.update { it.copy(toast = WzToast(WzToastKind.Success, successMessage)) }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(error = t.message ?: "保存失败", toast = WzToast(WzToastKind.Failure, t.message ?: "保存失败"))
                    }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    fun dispose() {
        scope.cancel()
    }
}
