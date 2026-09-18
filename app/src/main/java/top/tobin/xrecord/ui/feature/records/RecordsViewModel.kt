package top.tobin.xrecord.ui.feature.records

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.core.util.AccountingPeriod
import top.tobin.xrecord.core.util.DateTimeUtils
import top.tobin.xrecord.core.util.PeriodCalculator
import top.tobin.xrecord.data.local.dao.TransactionDetail
import top.tobin.xrecord.data.local.entity.TransactionEntity
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.data.preferences.SettingsDataSource
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.BookRepository
import top.tobin.xrecord.data.repository.TransactionRepository

/** 同一天的流水与当日小计。 */
data class DayGroup(
    val date: LocalDate,
    val expense: Long,
    val income: Long,
    val items: List<TransactionDetail>,
)

data class RecordsUiState(
    val isLoading: Boolean = true,
    val periodLabel: String = "",
    val income: Long = 0L,
    val expense: Long = 0L,
    val balance: Long = 0L,
    val groups: List<DayGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val bookRepository: BookRepository,
    settings: SettingsDataSource,
) : ViewModel() {

    private val periodOffset = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RecordsUiState> = authRepository.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(RecordsUiState(isLoading = false))
            } else {
                combine(
                    bookRepository.observeCurrentBook(userId),
                    settings.periodStartDay,
                    periodOffset,
                ) { book, startDay, offset -> Triple(book, startDay, offset) }
                    .flatMapLatest { (book, startDay, offset) ->
                        if (book == null) {
                            flowOf(RecordsUiState(isLoading = false))
                        } else {
                            val period = currentPeriod(startDay, offset)
                            transactionRepository
                                .observeDetails(userId, book.id, period)
                                .map { details -> buildState(period, startDay, details) }
                        }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsUiState(),
        )

    fun showPreviousPeriod() {
        periodOffset.value -= 1
    }

    fun showNextPeriod() {
        periodOffset.value += 1
    }

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch { transactionRepository.delete(transaction) }
    }

    fun restore(transaction: TransactionEntity) {
        viewModelScope.launch { transactionRepository.restore(transaction) }
    }

    private fun currentPeriod(startDay: Int, offset: Long): AccountingPeriod =
        PeriodCalculator.shift(PeriodCalculator.currentPeriod(startDay), offset)

    private fun buildState(
        period: AccountingPeriod,
        startDay: Int,
        details: List<TransactionDetail>,
    ): RecordsUiState {
        // 合计直接从同一份列表算出来，保证顶部汇总与下方每一行永远对得上。
        // 转账不计入收支，它只改变账户之间的余额分布。
        val counted = details.filter { !it.transaction.excludedFromStats }
        val expense = counted
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amount }
        val income = counted
            .filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amount }

        val groups = details
            .groupBy { DateTimeUtils.toLocalDate(it.transaction.occurredAt) }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                DayGroup(
                    date = date,
                    expense = items
                        .filter {
                            it.transaction.type == TransactionType.EXPENSE &&
                                !it.transaction.excludedFromStats
                        }
                        .sumOf { it.transaction.amount },
                    income = items
                        .filter {
                            it.transaction.type == TransactionType.INCOME &&
                                !it.transaction.excludedFromStats
                        }
                        .sumOf { it.transaction.amount },
                    items = items,
                )
            }

        return RecordsUiState(
            isLoading = false,
            periodLabel = DateTimeUtils.periodLabel(period, startDay),
            income = income,
            expense = expense,
            balance = income - expense,
            groups = groups,
        )
    }
}
