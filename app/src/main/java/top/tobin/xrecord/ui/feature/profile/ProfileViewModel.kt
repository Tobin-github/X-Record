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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.data.preferences.SettingsDataSource
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.TransactionRepository

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

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
