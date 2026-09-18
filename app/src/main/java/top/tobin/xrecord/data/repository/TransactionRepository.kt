package top.tobin.xrecord.data.repository

import androidx.room.withTransaction
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.core.util.AccountingPeriod
import top.tobin.xrecord.data.local.XRecordDatabase
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.dao.TransactionDetail
import top.tobin.xrecord.data.local.entity.TransactionEntity
import top.tobin.xrecord.data.local.entity.TransactionType

/** 一笔待保存的流水。`id` 为空表示新增，否则为编辑。 */
data class TransactionDraft(
    val id: Long? = null,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val toAccountId: Long?,
    val occurredAt: Long,
    val remark: String?,
)

enum class TransactionError {
    AMOUNT_INVALID,
    CATEGORY_REQUIRED,
    ACCOUNT_REQUIRED,
    TRANSFER_TARGET_REQUIRED,
    TRANSFER_SAME_ACCOUNT,
    NO_DEFAULT_BOOK,
    UNKNOWN,
}

sealed interface SaveTransactionResult {
    data class Success(val transactionId: Long) : SaveTransactionResult

    data class Failure(val error: TransactionError) : SaveTransactionResult
}

@Singleton
class TransactionRepository @Inject constructor(
    private val database: XRecordDatabase,
    private val transactionDao: TransactionDao,
    private val bookDao: BookDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * 某个账期内的流水明细。
     *
     * 账本暂取用户的默认账本；账本切换在 P3 实现，届时把 bookId 变成可选项即可。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDetails(userId: Long, period: AccountingPeriod): Flow<List<TransactionDetail>> =
        bookDao.observeDefault(userId).flatMapLatest { book ->
            if (book == null) {
                flowOf(emptyList())
            } else {
                transactionDao.observeDetailsInRange(
                    userId = userId,
                    bookId = book.id,
                    startInclusive = period.startEpochMillis(zone),
                    endExclusive = period.endExclusiveEpochMillis(zone),
                )
            }
        }

    fun observeDetail(transactionId: Long): Flow<TransactionDetail?> =
        transactionDao.observeDetail(transactionId)

    suspend fun findById(transactionId: Long): TransactionEntity? =
        withContext(ioDispatcher) { transactionDao.findById(transactionId) }

    suspend fun save(userId: Long, draft: TransactionDraft): SaveTransactionResult =
        withContext(ioDispatcher) {
            validate(draft)?.let { return@withContext SaveTransactionResult.Failure(it) }

            val bookId = bookDao.findDefault(userId)?.id
                ?: return@withContext SaveTransactionResult.Failure(TransactionError.NO_DEFAULT_BOOK)

            val now = System.currentTimeMillis()
            try {
                val transactionId = database.withTransaction {
                    val existing = draft.id?.let { transactionDao.findById(it) }
                    val entity = TransactionEntity(
                        id = existing?.id ?: 0L,
                        userId = userId,
                        bookId = bookId,
                        type = draft.type,
                        amount = draft.amount,
                        categoryId = if (draft.type == TransactionType.TRANSFER) null else draft.categoryId,
                        accountId = requireNotNull(draft.accountId),
                        toAccountId = if (draft.type == TransactionType.TRANSFER) draft.toAccountId else null,
                        // 转账手续费暂未开放录入，先保留已有值，避免编辑时被清空
                        transferFee = existing?.transferFee,
                        // 允许用户把流水改到别的日期，但创建时间不可变
                        occurredAt = draft.occurredAt,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        remark = draft.remark?.trim()?.takeIf { it.isNotEmpty() },
                        payee = existing?.payee,
                        excludedFromStats = existing?.excludedFromStats ?: false,
                        recurringRuleId = existing?.recurringRuleId,
                    )

                    if (existing == null) {
                        transactionDao.insert(entity)
                    } else {
                        transactionDao.update(entity)
                        entity.id
                    }
                }
                SaveTransactionResult.Success(transactionId)
            } catch (exception: Exception) {
                SaveTransactionResult.Failure(TransactionError.UNKNOWN)
            }
        }

    suspend fun delete(transaction: TransactionEntity) = withContext(ioDispatcher) {
        transactionDao.delete(transaction)
    }

    /**
     * 撤销删除。
     *
     * 保留原 id 重新插入，这样列表里其它引用（例如定期账单的来源标记）不会错位。
     */
    suspend fun restore(transaction: TransactionEntity) = withContext(ioDispatcher) {
        transactionDao.insert(transaction)
    }

    private fun validate(draft: TransactionDraft): TransactionError? = when {
        draft.amount <= 0L -> TransactionError.AMOUNT_INVALID
        draft.accountId == null -> TransactionError.ACCOUNT_REQUIRED
        draft.type == TransactionType.TRANSFER && draft.toAccountId == null ->
            TransactionError.TRANSFER_TARGET_REQUIRED

        // 转出转入是同一个账户时余额不变，却能凭空产生两条记录，必须拦住
        draft.type == TransactionType.TRANSFER && draft.toAccountId == draft.accountId ->
            TransactionError.TRANSFER_SAME_ACCOUNT

        draft.type != TransactionType.TRANSFER && draft.categoryId == null ->
            TransactionError.CATEGORY_REQUIRED

        else -> null
    }
}
