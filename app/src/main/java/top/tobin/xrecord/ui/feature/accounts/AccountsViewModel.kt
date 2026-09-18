package top.tobin.xrecord.ui.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.entity.AccountEntity
import top.tobin.xrecord.data.repository.AccountDraft
import top.tobin.xrecord.data.repository.AccountError
import top.tobin.xrecord.data.repository.AccountRepository
import top.tobin.xrecord.data.repository.AccountResult
import top.tobin.xrecord.data.repository.AuthRepository

enum class AccountsMessage {
    NAME_EMPTY,
    HAS_TRANSACTIONS,
    LAST_ACCOUNT,
}

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountWithBalance> = emptyList(),
    val message: AccountsMessage? = null,
) {
    val active: List<AccountWithBalance> get() = accounts.filter { !it.account.isArchived }

    val archived: List<AccountWithBalance> get() = accounts.filter { it.account.isArchived }
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val messageFlow = MutableStateFlow<AccountsMessage?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AccountsUiState> = authRepository.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(AccountsUiState(isLoading = false))
            } else {
                accountRepository.observeAccounts(userId)
                    .map { AccountsUiState(isLoading = false, accounts = it) }
            }
        }
        .combine(messageFlow) { state, message -> state.copy(message = message) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountsUiState(),
        )

    fun save(account: AccountEntity?, draft: AccountDraft) {
        viewModelScope.launch {
            val userId = authRepository.currentUserId.first() ?: return@launch
            val result = if (account == null) {
                accountRepository.create(userId, draft)
            } else {
                accountRepository.update(account, draft)
            }
            handle(result)
        }
    }

    fun setArchived(accountId: Long, archived: Boolean) {
        viewModelScope.launch { accountRepository.setArchived(accountId, archived) }
    }

    fun delete(account: AccountEntity) {
        viewModelScope.launch {
            val userId = authRepository.currentUserId.first() ?: return@launch
            handle(accountRepository.delete(userId, account))
        }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    private fun handle(result: AccountResult) {
        if (result is AccountResult.Failure) {
            messageFlow.value = when (result.error) {
                AccountError.NAME_EMPTY -> AccountsMessage.NAME_EMPTY
                AccountError.HAS_TRANSACTIONS -> AccountsMessage.HAS_TRANSACTIONS
                AccountError.LAST_ACCOUNT -> AccountsMessage.LAST_ACCOUNT
                AccountError.UNKNOWN -> AccountsMessage.NAME_EMPTY
            }
        }
    }
}
