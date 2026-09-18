package top.tobin.xrecord.ui.feature.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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
import top.tobin.xrecord.core.util.AccountingPeriod
import top.tobin.xrecord.core.util.DateTimeUtils
import top.tobin.xrecord.core.util.PeriodCalculator
import top.tobin.xrecord.data.local.dao.TransactionDetail
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.local.entity.BudgetEntity
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.data.preferences.SettingsDataSource
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.BookError
import top.tobin.xrecord.data.repository.BookRepository
import top.tobin.xrecord.data.repository.BookResult
import top.tobin.xrecord.data.repository.BudgetRepository
import top.tobin.xrecord.data.repository.LedgerRepository
import top.tobin.xrecord.data.repository.TransactionRepository

/** 预算进度。`overBudget` 由 UI 决定是否高亮，这里只给事实。 */
data class BudgetProgress(
    val budget: BudgetEntity,
    val categoryName: String?,
    val categoryColor: String?,
    val spent: Long,
    val ratio: Float,
) {
    val remaining: Long get() = budget.amount - spent

    val overBudget: Boolean get() = spent > budget.amount
}

data class BillsUiState(
    val isLoading: Boolean = true,
    val books: List<BookEntity> = emptyList(),
    val currentBookId: Long? = null,
    val periodLabel: String = "",
    val income: Long = 0L,
    val expense: Long = 0L,
    val balance: Long = 0L,
    val dailyAverage: Long = 0L,
    val previousExpense: Long = 0L,
    val totalBudget: BudgetProgress? = null,
    val categoryBudgets: List<BudgetProgress> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val pendingDeleteBook: BookEntity? = null,
    val pendingDeleteCount: Int = 0,
    val message: BillsMessage? = null,
) {
    /** 环比。上一账期没有支出时无从比较，返回 null 而不是显示 -100%。 */
    val monthOverMonth: Float?
        get() = if (previousExpense > 0L) {
            (expense - previousExpense).toFloat() / previousExpense
        } else {
            null
        }
}

enum class BillsMessage {
    BOOK_NAME_EMPTY,
    LAST_BOOK_CANNOT_DELETE,
}

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookRepository: BookRepository,
    private val budgetRepository: BudgetRepository,
    private val ledgerRepository: LedgerRepository,
    private val transactionRepository: TransactionRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

    private data class BookContext(
        val userId: Long,
        val books: List<BookEntity>,
        val currentBook: BookEntity?,
        val startDay: Int,
        val period: AccountingPeriod,
        val categories: List<CategoryEntity>,
    )

    private val periodOffset = MutableStateFlow(0L)
    private val messageFlow = MutableStateFlow<BillsMessage?>(null)
    private val pendingDeleteBookFlow = MutableStateFlow<BookEntity?>(null)
    private val pendingDeleteCountFlow = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val bookContextFlow = authRepository.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                combine(
                    bookRepository.observeBooks(userId),
                    bookRepository.observeCurrentBook(userId),
                    settings.periodStartDay,
                    periodOffset,
                ) { books, currentBook, startDay, offset ->
                    BookContext(
                        userId = userId,
                        books = books,
                        currentBook = currentBook,
                        startDay = startDay,
                        period = PeriodCalculator.shift(
                            PeriodCalculator.currentPeriod(startDay),
                            offset,
                        ),
                        categories = emptyList(),
                    )
                }.combine(ledgerRepository.observeAllCategories(userId)) { context, categories ->
                    context.copy(categories = categories)
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BillsUiState> = bookContextFlow
        .flatMapLatest { context ->
            val book = context?.currentBook
            if (context == null || book == null) {
                flowOf(BillsUiState(isLoading = false))
            } else {
                val previousPeriod = PeriodCalculator.shift(context.period, -1)
                combine(
                    transactionRepository.observeDetails(context.userId, book.id, context.period),
                    transactionRepository.observeDetails(
                        context.userId,
                        book.id,
                        previousPeriod,
                    ),
                    budgetRepository.observeBudgets(context.userId, book.id),
                ) { details, previousDetails, budgets ->
                    buildState(context, details, previousDetails, budgets)
                }
            }
        }
        .combine(messageFlow) { state, message -> state.copy(message = message) }
        .combine(pendingDeleteBookFlow) { state, book -> state.copy(pendingDeleteBook = book) }
        .combine(pendingDeleteCountFlow) { state, count -> state.copy(pendingDeleteCount = count) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BillsUiState(),
        )

    fun showPreviousPeriod() {
        periodOffset.value -= 1
    }

    fun showNextPeriod() {
        periodOffset.value += 1
    }

    fun selectBook(bookId: Long) {
        viewModelScope.launch { bookRepository.selectBook(bookId) }
    }

    fun createBook(name: String) {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUserId.first() ?: return@launch
            handleBookResult(bookRepository.createBook(currentUserId, name))
        }
    }

    fun renameBook(bookId: Long, name: String) {
        viewModelScope.launch { handleBookResult(bookRepository.renameBook(bookId, name)) }
    }

    fun setDefaultBook(bookId: Long) {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUserId.first() ?: return@launch
            handleBookResult(bookRepository.setDefaultBook(currentUserId, bookId))
        }
    }

    /** 删除前先取流水条数，确认框里要如实告诉用户会连带删掉多少条记录。 */
    fun requestDeleteBook(book: BookEntity) {
        viewModelScope.launch {
            pendingDeleteBookFlow.value = book
            pendingDeleteCountFlow.value = bookRepository.countTransactions(book.id)
        }
    }

    fun cancelDeleteBook() {
        pendingDeleteBookFlow.value = null
        pendingDeleteCountFlow.value = 0
    }

    fun confirmDeleteBook() {
        val book = pendingDeleteBookFlow.value ?: return
        viewModelScope.launch {
            val currentUserId = authRepository.currentUserId.first() ?: return@launch
            handleBookResult(bookRepository.deleteBook(currentUserId, book.id))
            cancelDeleteBook()
        }
    }

    fun setTotalBudget(amount: Long) {
        val bookId = uiState.value.currentBookId ?: return
        viewModelScope.launch {
            val userId = authRepository.currentUserId.first() ?: return@launch
            budgetRepository.setBudget(userId, bookId, categoryId = null, amount = amount)
        }
    }

    fun setCategoryBudget(categoryId: Long, amount: Long) {
        val bookId = uiState.value.currentBookId ?: return
        viewModelScope.launch {
            val userId = authRepository.currentUserId.first() ?: return@launch
            budgetRepository.setBudget(userId, bookId, categoryId = categoryId, amount = amount)
        }
    }

    fun removeBudget(budget: BudgetEntity) {
        viewModelScope.launch { budgetRepository.removeBudget(budget) }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    private fun handleBookResult(result: BookResult) {
        when (result) {
            is BookResult.Success -> Unit
            is BookResult.Failure -> messageFlow.value = when (result.error) {
                BookError.NAME_EMPTY -> BillsMessage.BOOK_NAME_EMPTY
                BookError.LAST_BOOK -> BillsMessage.LAST_BOOK_CANNOT_DELETE
                BookError.UNKNOWN -> BillsMessage.BOOK_NAME_EMPTY
            }
        }
    }

    private fun buildState(
        context: BookContext,
        details: List<TransactionDetail>,
        previousDetails: List<TransactionDetail>,
        budgets: List<BudgetEntity>,
    ): BillsUiState {
        val counted = details.filter { !it.transaction.excludedFromStats }
        val expense = counted
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amount }
        val income = counted
            .filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amount }

        val previousExpense = previousDetails
            .filter { !it.transaction.excludedFromStats }
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amount }

        val categoryNames = context.categories.associateBy { it.id }
        val progress = budgets.map { budget ->
            val spent = counted
                .filter { it.transaction.type == TransactionType.EXPENSE }
                .filter { budget.categoryId == null || it.transaction.categoryId == budget.categoryId }
                .sumOf { it.transaction.amount }
            val category = budget.categoryId?.let { categoryNames[it] }
            BudgetProgress(
                budget = budget,
                categoryName = category?.name,
                categoryColor = category?.color,
                spent = spent,
                ratio = if (budget.amount > 0L) spent.toFloat() / budget.amount else 0f,
            )
        }

        return BillsUiState(
            isLoading = false,
            books = context.books,
            currentBookId = context.currentBook?.id,
            periodLabel = DateTimeUtils.periodLabel(context.period, context.startDay),
            income = income,
            expense = expense,
            balance = income - expense,
            dailyAverage = dailyAverage(expense, context.period),
            previousExpense = previousExpense,
            totalBudget = progress.firstOrNull { it.budget.categoryId == null },
            categoryBudgets = progress.filter { it.budget.categoryId != null },
            expenseCategories = context.categories.filter {
                it.type == CategoryType.EXPENSE && it.parentId == null
            },
        )
    }

    /**
     * 日均支出。
     *
     * 当前账期只按已经过去的天数平均——月初第 3 天把总支出除以 30 会显著低估，
     * 让用户误以为还有很大空间。
     */
    private fun dailyAverage(expense: Long, period: AccountingPeriod): Long {
        val today = LocalDate.now()
        val days = if (period.contains(today)) {
            java.time.temporal.ChronoUnit.DAYS.between(period.start, today) + 1
        } else {
            java.time.temporal.ChronoUnit.DAYS.between(period.start, period.endExclusive)
        }
        return if (days > 0) expense / days else 0L
    }
}
