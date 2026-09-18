package top.tobin.xrecord.ui.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType
import top.tobin.xrecord.data.local.entity.RecurringRuleEntity
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.LedgerRepository
import top.tobin.xrecord.data.repository.RecurringRuleDraft
import top.tobin.xrecord.data.repository.RecurringRuleError
import top.tobin.xrecord.data.repository.RecurringRuleRepository
import top.tobin.xrecord.data.repository.RecurringRuleResult

enum class RecurringMessage {
    NAME_EMPTY,
    AMOUNT_INVALID,
    CATEGORY_REQUIRED,
    ACCOUNT_REQUIRED,
    TRANSFER_TARGET_REQUIRED,
    TRANSFER_SAME_ACCOUNT,
    NO_BOOK,
    UNKNOWN,
}

data class RecurringRulesUiState(
    val isLoading: Boolean = true,
    val rules: List<RecurringRuleEntity> = emptyList(),
    val accounts: List<AccountWithBalance> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val message: RecurringMessage? = null,
)

@HiltViewModel
class RecurringRulesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val messageFlow = MutableStateFlow<RecurringMessage?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RecurringRulesUiState> = authRepository.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(RecurringRulesUiState(isLoading = false))
            } else {
                combine(
                    recurringRuleRepository.observeRules(userId),
                    ledgerRepository.observeAccounts(userId),
                    ledgerRepository.observeCategories(userId, CategoryType.EXPENSE),
                    ledgerRepository.observeCategories(userId, CategoryType.INCOME),
                ) { rules, accounts, expenseCategories, incomeCategories ->
                    RecurringRulesUiState(
                        isLoading = false,
                        rules = rules,
                        accounts = accounts,
                        expenseCategories = expenseCategories,
                        incomeCategories = incomeCategories,
                    )
                }
            }
        }
        .combine(messageFlow) { state, message -> state.copy(message = message) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecurringRulesUiState(),
        )

    init {
        // 打开本页时先补齐到期账单，用户看到的就是最新的
        viewModelScope.launch {
            authRepository.currentUserId.first()?.let { userId ->
                recurringRuleRepository.generateDue(userId)
            }
        }
    }

    fun save(existingId: Long?, draft: RecurringRuleDraft) {
        viewModelScope.launch {
            val userId = authRepository.currentUserId.first() ?: return@launch
            when (val result = recurringRuleRepository.save(userId, existingId, draft)) {
                is RecurringRuleResult.Success -> Unit
                is RecurringRuleResult.Failure -> messageFlow.value = result.error.toMessage()
            }
        }
    }

    fun setEnabled(rule: RecurringRuleEntity, enabled: Boolean) {
        viewModelScope.launch {
            recurringRuleRepository.setEnabled(rule, enabled)
            if (enabled) {
                // 刚启用的规则可能已经错过若干期，立即补上
                authRepository.currentUserId.first()?.let { userId ->
                    recurringRuleRepository.generateDue(userId)
                }
            }
        }
    }

    fun delete(ruleId: Long) {
        viewModelScope.launch { recurringRuleRepository.delete(ruleId) }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }
}

private fun RecurringRuleError.toMessage(): RecurringMessage = when (this) {
    RecurringRuleError.NAME_EMPTY -> RecurringMessage.NAME_EMPTY
    RecurringRuleError.AMOUNT_INVALID -> RecurringMessage.AMOUNT_INVALID
    RecurringRuleError.CATEGORY_REQUIRED -> RecurringMessage.CATEGORY_REQUIRED
    RecurringRuleError.ACCOUNT_REQUIRED -> RecurringMessage.ACCOUNT_REQUIRED
    RecurringRuleError.TRANSFER_TARGET_REQUIRED -> RecurringMessage.TRANSFER_TARGET_REQUIRED
    RecurringRuleError.TRANSFER_SAME_ACCOUNT -> RecurringMessage.TRANSFER_SAME_ACCOUNT
    RecurringRuleError.NO_BOOK -> RecurringMessage.NO_BOOK
    RecurringRuleError.UNKNOWN -> RecurringMessage.UNKNOWN
}
