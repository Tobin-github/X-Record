package top.tobin.xrecord.ui.feature.charts

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
import top.tobin.xrecord.core.util.AccountingPeriod
import top.tobin.xrecord.core.util.ChartRange
import top.tobin.xrecord.core.util.ChartRangeCalculator
import top.tobin.xrecord.core.util.DateTimeUtils
import top.tobin.xrecord.data.local.dao.TransactionDetail
import top.tobin.xrecord.data.local.entity.TransactionType
import top.tobin.xrecord.data.preferences.SettingsDataSource
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.BookRepository
import top.tobin.xrecord.data.repository.TransactionRepository

/** 趋势图上的一个点：同一时间桶内的收支合计。 */
data class TrendPoint(
    val label: String,
    val expense: Long,
    val income: Long,
)

/** 分类聚合结果，环形图与排行共用同一份。 */
data class CategorySlice(
    val categoryId: Long?,
    val name: String?,
    val colorHex: String?,
    val amount: Long,
    val ratio: Float,
)

data class ChartsUiState(
    val isLoading: Boolean = true,
    val range: ChartRange = ChartRange.MONTH,
    val rangeLabel: String = "",
    /** 横轴标签需要由区间起点推算，因此一并带出来。 */
    val periodStart: LocalDate? = null,
    val categoryType: TransactionType = TransactionType.EXPENSE,
    val trend: List<TrendPoint> = emptyList(),
    val slices: List<CategorySlice> = emptyList(),
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
) {
    val isEmpty: Boolean get() = trend.all { it.expense == 0L && it.income == 0L }
}

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val bookRepository: BookRepository,
    private val transactionRepository: TransactionRepository,
    private val settings: SettingsDataSource,
) : ViewModel() {

    private data class QueryParams(
        val range: ChartRange,
        val offset: Long,
        val categoryType: TransactionType,
    )

    private val rangeFlow = MutableStateFlow(ChartRange.MONTH)
    private val offsetFlow = MutableStateFlow(0L)
    private val categoryTypeFlow = MutableStateFlow(TransactionType.EXPENSE)

    private val paramsFlow = combine(
        rangeFlow,
        offsetFlow,
        categoryTypeFlow,
    ) { range, offset, categoryType -> QueryParams(range, offset, categoryType) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChartsUiState> = authRepository.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(ChartsUiState(isLoading = false))
            } else {
                combine(
                    bookRepository.observeCurrentBook(userId),
                    settings.periodStartDay,
                    paramsFlow,
                ) { book, startDay, params -> Triple(book, startDay, params) }
                    .flatMapLatest { (book, startDay, params) ->
                        if (book == null) {
                            flowOf(ChartsUiState(isLoading = false))
                        } else {
                            val today = LocalDate.now()
                            val period = ChartRangeCalculator.range(
                                range = params.range,
                                offset = params.offset,
                                startDay = startDay,
                                today = today,
                            )
                            transactionRepository
                                .observeDetails(userId, book.id, period)
                                .map { details -> buildState(params, period, startDay, details) }
                        }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChartsUiState(),
        )

    /** 切换维度时把偏移归零，否则从"上个月"切到"年"会跳到意料之外的年份。 */
    fun setRange(range: ChartRange) {
        if (range == rangeFlow.value) return
        rangeFlow.value = range
        offsetFlow.value = 0L
    }

    fun setCategoryType(type: TransactionType) {
        categoryTypeFlow.value = type
    }

    fun showPreviousRange() {
        offsetFlow.value -= 1
    }

    fun showNextRange() {
        offsetFlow.value += 1
    }

    private fun buildState(
        params: QueryParams,
        period: AccountingPeriod,
        startDay: Int,
        details: List<TransactionDetail>,
    ): ChartsUiState {
        val counted = details.filter { !it.transaction.excludedFromStats }

        val totalExpense = counted
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amount }
        val totalIncome = counted
            .filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amount }

        val buckets = ChartRangeCalculator.buckets(params.range, period)
        val expenseByBucket = LongArray(buckets.size)
        val incomeByBucket = LongArray(buckets.size)

        counted.forEach { detail ->
            val index = ChartRangeCalculator.bucketIndex(
                buckets,
                DateTimeUtils.toLocalDate(detail.transaction.occurredAt),
            )
            if (index < 0) return@forEach
            when (detail.transaction.type) {
                TransactionType.EXPENSE ->
                    expenseByBucket[index] += detail.transaction.amount

                TransactionType.INCOME ->
                    incomeByBucket[index] += detail.transaction.amount

                TransactionType.TRANSFER -> Unit
            }
        }

        val ofType = counted.filter { it.transaction.type == params.categoryType }
        val typeTotal = ofType.sumOf { it.transaction.amount }
        val slices = ofType
            .groupBy { it.transaction.categoryId }
            .map { (categoryId, items) ->
                val amount = items.sumOf { it.transaction.amount }
                CategorySlice(
                    categoryId = categoryId,
                    name = items.first().categoryName,
                    colorHex = items.first().categoryColor,
                    amount = amount,
                    ratio = if (typeTotal > 0L) amount.toFloat() / typeTotal else 0f,
                )
            }
            .sortedByDescending { it.amount }

        return ChartsUiState(
            isLoading = false,
            range = params.range,
            rangeLabel = ChartRangeCalculator.label(params.range, period, startDay),
            periodStart = period.start,
            categoryType = params.categoryType,
            trend = buckets.mapIndexed { index, bucket ->
                TrendPoint(
                    label = bucket.label,
                    expense = expenseByBucket[index],
                    income = incomeByBucket[index],
                )
            },
            slices = slices,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
        )
    }
}
