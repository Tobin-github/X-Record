package top.tobin.xrecord.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.data.local.dao.LocalAccount
import top.tobin.xrecord.data.repository.AuthResult
import top.tobin.xrecord.data.preferences.SettingsDataSource
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.TransactionRepository

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

    /** 本机已注册的账号，用于切换账号。 */
    val localAccounts: StateFlow<List<LocalAccount>> = authRepository.observeLocalAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 每月起始日。它同时影响明细分组、图表聚合与预算周期，统一由 DataStore 提供。 */
    val periodStartDay: StateFlow<Int> = settings.periodStartDay
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 1,
        )

    private val _cleared = MutableStateFlow(false)

    /** 清空完成的一次性信号，UI 消费后调用 [consumeCleared]。 */
    val cleared: StateFlow<Boolean> = _cleared.asStateFlow()

    private val _switchFailed = MutableStateFlow(false)

    /** 切换账号时密码校验失败。 */
    val switchFailed: StateFlow<Boolean> = _switchFailed.asStateFlow()

    fun switchAccount(userId: Long, password: String) {
        viewModelScope.launch {
            when (authRepository.switchAccount(userId, password)) {
                // 切换成功后根导航会因 user 变化重建整棵界面树，
                // 这里不需要再做任何跳转或清理
                is AuthResult.Success -> _switchFailed.value = false
                is AuthResult.Failure -> _switchFailed.value = true
            }
        }
    }

    fun clearSwitchError() {
        _switchFailed.value = false
    }

    fun setPeriodStartDay(day: Int) {
        viewModelScope.launch { settings.setPeriodStartDay(day) }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            val userId = authRepository.currentUserId.first() ?: return@launch
            transactionRepository.clearAll(userId)
            _cleared.value = true
        }
    }

    fun consumeCleared() {
        _cleared.value = false
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
