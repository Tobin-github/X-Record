package top.tobin.xrecord.data.repository

import androidx.room.withTransaction
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.core.util.RecurringSchedule
import top.tobin.xrecord.data.local.XRecordDatabase
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.dao.RecurringRuleDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.entity.RecurringFrequency
import top.tobin.xrecord.data.local.entity.RecurringRuleEntity
import top.tobin.xrecord.data.local.entity.TransactionEntity
import top.tobin.xrecord.data.local.entity.TransactionType

/** 新建或编辑定期账单时用户填写的内容。 */
data class RecurringRuleDraft(
    val name: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val toAccountId: Long?,
    val frequency: RecurringFrequency,
    val interval: Int,
    val nextTriggerAt: Long,
    val remark: String?,
    val autoCreate: Boolean,
)

enum class RecurringRuleError {
    NAME_EMPTY,
    AMOUNT_INVALID,
    ACCOUNT_REQUIRED,
    CATEGORY_REQUIRED,
    TRANSFER_TARGET_REQUIRED,
    TRANSFER_SAME_ACCOUNT,
    NO_BOOK,
    UNKNOWN,
}

sealed interface RecurringRuleResult {
    data object Success : RecurringRuleResult

    data class Failure(val error: RecurringRuleError) : RecurringRuleResult
}

@Singleton
class RecurringRuleRepository @Inject constructor(
    private val database: XRecordDatabase,
    private val recurringRuleDao: RecurringRuleDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val bookRepository: BookRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun observeRules(userId: Long): Flow<List<RecurringRuleEntity>> =
        recurringRuleDao.observeRules(userId)

    suspend fun save(
        userId: Long,
        existingId: Long?,
        draft: RecurringRuleDraft,
    ): RecurringRuleResult = withContext(ioDispatcher) {
        val name = draft.name.trim()
        validate(draft, name)?.let { return@withContext RecurringRuleResult.Failure(it) }

        val bookId = bookRepository.resolveCurrentBook(userId)?.id
            ?: return@withContext RecurringRuleResult.Failure(RecurringRuleError.NO_BOOK)

        val anchorDay = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(draft.nextTriggerAt),
            zone,
        ).dayOfMonth

        val entity = RecurringRuleEntity(
            id = existingId ?: 0L,
            userId = userId,
            bookId = bookId,
            name = name,
            type = draft.type,
            amount = draft.amount,
            categoryId = if (draft.type == TransactionType.TRANSFER) null else draft.categoryId,
            accountId = requireNotNull(draft.accountId),
            toAccountId = if (draft.type == TransactionType.TRANSFER) draft.toAccountId else null,
            frequency = draft.frequency,
            interval = draft.interval.coerceAtLeast(1),
            anchorDay = anchorDay,
            remark = draft.remark?.trim()?.takeIf { it.isNotEmpty() },
            autoCreate = draft.autoCreate,
            isEnabled = true,
            nextTriggerAt = draft.nextTriggerAt,
            createdAt = System.currentTimeMillis(),
        )

        if (existingId == null) {
            recurringRuleDao.insert(entity)
        } else {
            // 编辑时保留原创建时间
            val created = recurringRuleDao.findAllForUser(userId)
                .firstOrNull { it.id == existingId }?.createdAt ?: entity.createdAt
            recurringRuleDao.update(entity.copy(createdAt = created))
        }
        RecurringRuleResult.Success
    }

    suspend fun setEnabled(rule: RecurringRuleEntity, enabled: Boolean) =
        withContext(ioDispatcher) {
            recurringRuleDao.update(rule.copy(isEnabled = enabled))
        }

    suspend fun delete(ruleId: Long) = withContext(ioDispatcher) {
        recurringRuleDao.deleteById(ruleId)
    }

    /**
     * 补齐所有已到期的定期账单。
     *
     * 应用没打开时不会自动生成，因此每次进入应用与进入本页时都跑一次"追补"：
     * 把错过的期数逐条补成流水，再把 nextTriggerAt 推到未来。
     *
     * @return 本次生成的流水条数
     */
    suspend fun generateDue(
        userId: Long,
        now: Long = System.currentTimeMillis(),
    ): Int = withContext(ioDispatcher) {
        database.withTransaction {
            val due = recurringRuleDao.findDue(userId, now)
            var created = 0

            due.forEach { rule ->
                var triggerAt = rule.nextTriggerAt
                var generated = 0

                while (triggerAt <= now && generated < MAX_CATCH_UP_PER_RUN) {
                    if (insertTransaction(rule, triggerAt, now)) {
                        created++
                        generated++
                    }
                    triggerAt = RecurringSchedule.nextOccurrence(
                        current = triggerAt,
                        frequency = rule.frequency,
                        interval = rule.interval,
                        anchorDay = rule.anchorDay,
                        zone = zone,
                    )
                }

                // 追补超过上限（例如规则停用了很久）时直接跳到未来，
                // 避免用户每次打开应用都被灌进一大批历史流水
                while (triggerAt <= now) {
                    triggerAt = RecurringSchedule.nextOccurrence(
                        current = triggerAt,
                        frequency = rule.frequency,
                        interval = rule.interval,
                        anchorDay = rule.anchorDay,
                        zone = zone,
                    )
                }

                recurringRuleDao.update(rule.copy(nextTriggerAt = triggerAt))
            }

            created
        }
    }

    private suspend fun insertTransaction(
        rule: RecurringRuleEntity,
        occurredAt: Long,
        now: Long,
    ): Boolean {
        // 分类可能已被删除，而 recurring_rules 对 categoryId 没有外键约束，
        // 直接写入会违反 transactions 的外键并导致整批生成失败
        val categoryId = rule.categoryId
            ?.takeIf { categoryDao.findById(it) != null }

        if (rule.type != TransactionType.TRANSFER && categoryId == null) return false

        transactionDao.insert(
            TransactionEntity(
                userId = rule.userId,
                bookId = rule.bookId,
                type = rule.type,
                amount = rule.amount,
                categoryId = categoryId,
                accountId = rule.accountId,
                toAccountId = rule.toAccountId,
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
                remark = rule.remark,
                recurringRuleId = rule.id,
            ),
        )
        return true
    }

    private fun validate(draft: RecurringRuleDraft, name: String): RecurringRuleError? = when {
        name.isEmpty() -> RecurringRuleError.NAME_EMPTY
        draft.amount <= 0L -> RecurringRuleError.AMOUNT_INVALID
        draft.accountId == null -> RecurringRuleError.ACCOUNT_REQUIRED
        draft.type == TransactionType.TRANSFER && draft.toAccountId == null ->
            RecurringRuleError.TRANSFER_TARGET_REQUIRED

        draft.type == TransactionType.TRANSFER && draft.toAccountId == draft.accountId ->
            RecurringRuleError.TRANSFER_SAME_ACCOUNT

        draft.type != TransactionType.TRANSFER && draft.categoryId == null ->
            RecurringRuleError.CATEGORY_REQUIRED

        else -> null
    }

    private companion object {
        /** 单次追补上限，防止长期未打开的应用一次性写入过多记录。 */
        const val MAX_CATCH_UP_PER_RUN = 36
    }
}
