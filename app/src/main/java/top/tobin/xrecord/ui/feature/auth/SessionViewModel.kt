package top.tobin.xrecord.ui.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.RecurringRuleRepository
import top.tobin.xrecord.data.repository.SessionState

@HiltViewModel
class SessionViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = authRepository.sessionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading,
        )

    init {
        // 应用冷启动时补齐到期的定期账单。
        // 没有用 WorkManager 做后台调度：本地应用不开就没有意义，
        // 打开时一次性追平既简单，也不会在用户不知情时写入流水。
        viewModelScope.launch {
            authRepository.currentUserId.filterNotNull().firstOrNull()?.let { userId ->
                recurringRuleRepository.generateDue(userId)
            }
        }
    }
}
