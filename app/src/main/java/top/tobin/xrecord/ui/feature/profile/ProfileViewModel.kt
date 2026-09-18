package top.tobin.xrecord.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.SessionState
import top.tobin.xrecord.data.preferences.SettingsDataSource

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.sessionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading,
        )

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    /** 每月起始日。它同时影响明细分组、图表聚合与预算周期，统一由 DataStore 提供。 */
    val periodStartDay: StateFlow<Int> = settings.periodStartDay
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 1,
        )

    fun setPeriodStartDay(day: Int) {
        viewModelScope.launch { settings.setPeriodStartDay(day) }
    }
}
