package top.tobin.xrecord.ui.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.tobin.xrecord.core.money.AmountInput
import top.tobin.xrecord.core.money.MoneyFormatter
import top.tobin.xrecord.core.util.DateTimeUtils
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.LedgerRepository
import top.tobin.xrecord.data.repository.SaveTransactionResult
import top.tobin.xrecord.data.repository.TransactionDraft
import top.tobin.xrecord.data.repository.TransactionError
import top.tobin.xrecord.data.repository.TransactionRepository
import top.tobin.xrecord.ui.navigation.ARG_TRANSACTION_ID

data class EditorUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val expression: String = "0",
    val amountCents: Long = 0L,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val remark: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountWithBalance> = emptyList(),
    val error: TransactionError? = null,
    val isSaving: Boolean = false,
    val savedTick: Int = 0,
) {
    val selectedAccount: AccountWithBalance?
        get() = accounts.firstOrNull { it.account.id == accountId }

    val selectedToAccount: AccountWithBalance?
        get() = accounts.firstOrNull { it.account.id == toAccountId }

    /** 下拉里可选的账户：已归档的账户只在它是当前选中项时保留，避免编辑中被悄悄换掉。 */
    val selectableAccounts: List<AccountWithBalance>
        get() = accounts.filter {
            !it.account.isArchived || it.account.id == accountId || it.account.id == toAccountId
        }

    val canSave: Boolean
        get() = !isSaving && !isLoading && amountCents > 0L && when (type) {
            TransactionType.TRANSFER ->
                accountId != null && toAccountId != null && accountId != toAccountId

            else -> categoryId != null && accountId != null
        }
}

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val ledgerRepository: LedgerRepository,
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 有 id 表示编辑已有流水，为空表示新增。 */
    private val editingId: Long? = savedStateHandle.get<Long>(ARG_TRANSACTION_ID)

    private val typeFlow = MutableStateFlow(TransactionType.EXPENSE)

    private val _uiState = MutableStateFlow(
        EditorUiState(
            isEditing = editingId != null,
            occurredAt = System.currentTimeMillis(),
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var userId: Long? = null

    init {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUserId.filterNotNull().firstOrNull()
            if (currentUserId == null) {
                _uiState.update { it.copy(isLoading = false, error = TransactionError.UNKNOWN) }
                return@launch
            }
            userId = currentUserId

            // 先载入已有流水，再开始监听参照数据，避免默认值把编辑内容覆盖掉
            if (editingId != null) loadExisting(editingId)
            observeReferenceData(currentUserId)
        }
    }

    fun onTypeChange(type: TransactionType) {
        if (type == _uiState.value.type) return
        typeFlow.value = type
        _uiState.update { it.copy(type = type, error = null) }
    }

    fun onKey(key: Char) {
        _uiState.update { state ->
            val next = if (AmountInput.isOperator(key)) {
                AmountInput.appendOperator(state.expression, key)
            } else if (key == '.') {
                AmountInput.appendDecimalPoint(state.expression)
            } else {
                AmountInput.appendDigit(state.expression, key)
            }
            state.withExpression(next)
        }
    }

    fun onBackspace() {
        _uiState.update { it.withExpression(AmountInput.backspace(it.expression)) }
    }

    /** 按键盘上的等号：把表达式收敛成结果。 */
    fun onEvaluate() {
        _uiState.update { state ->
            val result = AmountInput.evaluate(state.expression)
            if (result == null) {
                state
            } else {
                state.withExpression(MoneyFormatter.toPlainString(result))
            }
        }
    }

    fun onCategorySelect(categoryId: Long) {
        _uiState.update { it.copy(categoryId = categoryId, error = null) }
    }

    fun onAccountSelect(accountId: Long) {
        _uiState.update { it.copy(accountId = accountId, error = null) }
    }

    fun onToAccountSelect(accountId: Long) {
        _uiState.update { it.copy(toAccountId = accountId, error = null) }
    }

    fun onDateChange(occurredAt: Long) {
        _uiState.update { it.copy(occurredAt = occurredAt, error = null) }
    }

    fun onRemarkChange(remark: String) {
        _uiState.update { it.copy(remark = remark) }
    }

    fun save() {
        val state = _uiState.value
        val currentUserId = userId ?: return
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = transactionRepository.save(
                userId = currentUserId,
                draft = TransactionDraft(
                    id = editingId,
                    type = state.type,
                    amount = state.amountCents,
                    categoryId = state.categoryId,
                    accountId = state.accountId,
                    toAccountId = state.toAccountId,
                    occurredAt = state.occurredAt,
                    remark = state.remark,
                ),
            )
            _uiState.update { current ->
                when (result) {
                    is SaveTransactionResult.Success ->
                        current.copy(isSaving = false, savedTick = current.savedTick + 1)

                    is SaveTransactionResult.Failure ->
                        current.copy(isSaving = false, error = result.error)
                }
            }
        }
    }

    /**
     * 打开面板时重置为一次全新的记账：金额、备注清空，日期回到此刻。
     */
    fun startNewEntry() {
        _uiState.update {
            it.copy(
                expression = "0",
                amountCents = 0L,
                remark = "",
                occurredAt = System.currentTimeMillis(),
                error = null,
            )
        }
    }

    /**
     * 保存成功后为"再记一笔"做准备。
     *
     * 只清空金额与备注，保留类型、分类、账户和日期——连续录入同一天的几笔餐饮时，
     * 用户不必每次都重新选一遍。
     *
     * 日期保留但**时间刷新到此刻**：面板 ViewModel 的生命周期跟主界面一样长，
     * 若沿用旧时间，同一会话里所有流水都会带着最初打开面板那一刻的时间戳。
     */
    fun prepareForNextEntry() {
        _uiState.update {
            it.copy(
                expression = "0",
                amountCents = 0L,
                remark = "",
                occurredAt = DateTimeUtils.withDate(
                    epochMillis = System.currentTimeMillis(),
                    date = DateTimeUtils.toLocalDate(it.occurredAt),
                ),
                error = null,
            )
        }
    }

    private suspend fun loadExisting(transactionId: Long) {
        val detail = transactionRepository.observeDetail(transactionId).first()
        if (detail == null) {
            _uiState.update { it.copy(isLoading = false, error = TransactionError.UNKNOWN) }
            return
        }

        typeFlow.value = detail.transaction.type
        _uiState.update { state ->
            state.withExpression(MoneyFormatter.toPlainString(detail.transaction.amount)).copy(
                type = detail.transaction.type,
                categoryId = detail.transaction.categoryId,
                accountId = detail.transaction.accountId,
                toAccountId = detail.transaction.toAccountId,
                occurredAt = detail.transaction.occurredAt,
                remark = detail.transaction.remark.orEmpty(),
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeReferenceData(currentUserId: Long) {
        viewModelScope.launch {
            ledgerRepository.observeAccounts(currentUserId).collect { accounts ->
                _uiState.update { state ->
                    val accountId = state.accountId
                        ?.takeIf { id -> accounts.any { it.account.id == id } }
                        ?: accounts.firstOrNull { !it.account.isArchived }?.account?.id
                    val toAccountId = state.toAccountId
                        ?.takeIf { id -> accounts.any { it.account.id == id } }
                        ?: accounts.filter { !it.account.isArchived && it.account.id != accountId }
                            .firstOrNull()?.account?.id

                    state.copy(
                        accounts = accounts,
                        accountId = accountId,
                        toAccountId = toAccountId,
                        isLoading = false,
                    )
                }
            }
        }

        viewModelScope.launch {
            typeFlow
                .flatMapLatest { type ->
                    if (type == TransactionType.TRANSFER) {
                        flowOf(emptyList())
                    } else {
                        ledgerRepository.observeCategories(currentUserId, type.toCategoryType())
                    }
                }
                .collect { categories ->
                    _uiState.update { state ->
                        val categoryId = state.categoryId
                            ?.takeIf { id -> categories.any { it.id == id } }
                            ?: categories.firstOrNull()?.id
                        state.copy(categories = categories, categoryId = categoryId)
                    }
                }
        }
    }

    private fun EditorUiState.withExpression(newExpression: String) = copy(
        expression = newExpression,
        amountCents = AmountInput.evaluate(newExpression) ?: 0L,
        error = null,
    )
}

fun TransactionType.toCategoryType(): CategoryType = when (this) {
    TransactionType.INCOME -> CategoryType.INCOME
    else -> CategoryType.EXPENSE
}
