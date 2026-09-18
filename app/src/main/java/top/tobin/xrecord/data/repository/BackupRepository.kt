package top.tobin.xrecord.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.data.backup.BackupAccount
import top.tobin.xrecord.data.backup.BackupBook
import top.tobin.xrecord.data.backup.BackupBudget
import top.tobin.xrecord.data.backup.BackupCategory
import top.tobin.xrecord.data.backup.BackupData
import top.tobin.xrecord.data.backup.BackupRecurringRule
import top.tobin.xrecord.data.backup.BackupSummary
import top.tobin.xrecord.data.backup.BackupTransaction
import top.tobin.xrecord.data.backup.ImportError
import top.tobin.xrecord.data.backup.ImportResult
import top.tobin.xrecord.data.local.XRecordDatabase
import top.tobin.xrecord.data.local.dao.AccountDao
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.BudgetDao
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.dao.RecurringRuleDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.entity.AccountEntity
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.local.entity.BudgetEntity
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.RecurringRuleEntity
import top.tobin.xrecord.data.local.entity.TransactionEntity

@Singleton
class BackupRepository @Inject constructor(
    private val database: XRecordDatabase,
    private val bookDao: BookDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val recurringRuleDao: RecurringRuleDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 备份文件用 JSON，可读、可手工检查，出问题时用户自己也能看懂一部分。
     *
     * `ignoreUnknownKeys` 让旧版本应用能读新版本导出的文件（多出来的字段直接忽略），
     * `encodeDefaults` 保证字段即使等于默认值也会写入，避免备份出现"缺字段"的歧义。
     */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportToJson(userId: Long, appVersionName: String): String =
        withContext(ioDispatcher) {
            // 在一个事务里读取全部表，避免导出过程中数据被改动导致前后不一致
            val data = database.withTransaction {
                BackupData(
                    formatVersion = BackupData.FORMAT_VERSION,
                    exportedAt = System.currentTimeMillis(),
                    appVersionName = appVersionName,
                    books = bookDao.findBooks(userId).map { it.toBackup() },
                    accounts = accountDao.findAccounts(userId).map { it.toBackup() },
                    categories = categoryDao.findAllForUser(userId).map { it.toBackup() },
                    transactions = transactionDao.findAllForUser(userId).map { it.toBackup() },
                    budgets = budgetDao.findAllForUser(userId).map { it.toBackup() },
                    recurringRules = recurringRuleDao.findAllForUser(userId).map { it.toBackup() },
                )
            }
            json.encodeToString(data)
        }

    /** 只解析与校验，不落库。用于导入前的预览与二次确认。 */
    fun parse(text: String): ImportResult = when (val decoded = decodeAndValidate(text)) {
        is Decoded.Ok -> ImportResult.Success(decoded.data.toSummary())
        is Decoded.Error -> decoded.result
    }

    /**
     * 用备份覆盖当前账号的数据。
     *
     * 会先清空当前账号名下的全部记录再写入——这是"恢复"的语义，
     * 调用方必须先让用户确认。所有 id 都会重新分配：
     * 主键在本库全局自增，沿用备份里的 id 会和既有记录撞车。
     */
    suspend fun importFromJson(userId: Long, text: String): ImportResult =
        withContext(ioDispatcher) {
            val data = when (val decoded = decodeAndValidate(text)) {
                is Decoded.Ok -> decoded.data
                is Decoded.Error -> return@withContext decoded.result
            }

            val summary = database.withTransaction {
                clearUserData(userId)
                restore(userId, data)
            }
            ImportResult.Success(summary)
        }

    private sealed interface Decoded {
        data class Ok(val data: BackupData) : Decoded

        data class Error(val result: ImportResult) : Decoded
    }

    private fun decodeAndValidate(text: String): Decoded {
        val data = try {
            json.decodeFromString<BackupData>(text)
        } catch (exception: SerializationException) {
            return Decoded.Error(ImportResult.InvalidFormat(ImportError.MALFORMED))
        } catch (exception: IllegalArgumentException) {
            return Decoded.Error(ImportResult.InvalidFormat(ImportError.MALFORMED))
        }

        return when {
            data.formatVersion > BackupData.FORMAT_VERSION ->
                Decoded.Error(
                    ImportResult.InvalidFormat(ImportError.UNSUPPORTED_VERSION),
                )

            // 结构完整性：缺账本、缺账户，或流水指向了不存在的记录，
            // 都会导致导入后出现"孤儿数据"，必须在写入前拦下
            !data.hasConsistentReferences() -> Decoded.Error(ImportResult.BrokenReferences)

            else -> Decoded.Ok(data)
        }
    }

    /**
     * 按外键依赖顺序清空。
     *
     * 流水与定期账单都引用了账户（`NO ACTION`），必须先删掉它们才能删账户；
     * 账本最后删，因为流水依赖它。
     */
    private suspend fun clearUserData(userId: Long) {
        transactionDao.deleteAllForUser(userId)
        recurringRuleDao.deleteAllForUser(userId)
        budgetDao.deleteAllForUser(userId)
        accountDao.deleteAllForUser(userId)
        categoryDao.deleteAllForUser(userId)
        bookDao.deleteAllForUser(userId)
    }

    private suspend fun restore(userId: Long, data: BackupData): BackupSummary {
        val bookIds = mutableMapOf<Long, Long>()
        data.books.forEach { book ->
            bookIds[book.id] = bookDao.insert(
                BookEntity(
                    userId = userId,
                    name = book.name,
                    icon = book.icon,
                    sortOrder = book.sortOrder,
                    isDefault = book.isDefault,
                    createdAt = book.createdAt,
                ),
            )
        }

        val accountIds = mutableMapOf<Long, Long>()
        data.accounts.forEach { account ->
            accountIds[account.id] = accountDao.insert(
                AccountEntity(
                    userId = userId,
                    name = account.name,
                    icon = account.icon,
                    color = account.color,
                    type = account.type,
                    initialBalance = account.initialBalance,
                    includeInTotal = account.includeInTotal,
                    cardTailNumber = account.cardTailNumber,
                    remark = account.remark,
                    sortOrder = account.sortOrder,
                    isArchived = account.isArchived,
                    createdAt = account.createdAt,
                ),
            )
        }

        // 分类先插父级再插子级，否则 parentId 找不到落脚点
        val categoryIds = mutableMapOf<Long, Long>()
        val sortedCategories = data.categories.sortedBy { it.parentId != null }
        sortedCategories.forEach { category ->
            categoryIds[category.id] = categoryDao.insert(
                CategoryEntity(
                    userId = userId,
                    parentId = category.parentId?.let { categoryIds[it] },
                    name = category.name,
                    icon = category.icon,
                    color = category.color,
                    type = category.type,
                    sortOrder = category.sortOrder,
                    isPreset = category.isPreset,
                    isHidden = category.isHidden,
                ),
            )
        }

        data.transactions.forEach { transaction ->
            transactionDao.insert(
                TransactionEntity(
                    userId = userId,
                    bookId = requireNotNull(bookIds[transaction.bookId]),
                    type = transaction.type,
                    amount = transaction.amount,
                    categoryId = transaction.categoryId?.let { categoryIds[it] },
                    accountId = requireNotNull(accountIds[transaction.accountId]),
                    toAccountId = transaction.toAccountId?.let { accountIds[it] },
                    transferFee = transaction.transferFee,
                    occurredAt = transaction.occurredAt,
                    createdAt = transaction.createdAt,
                    updatedAt = transaction.updatedAt,
                    remark = transaction.remark,
                    payee = transaction.payee,
                    excludedFromStats = transaction.excludedFromStats,
                ),
            )
        }

        data.budgets.forEach { budget ->
            budgetDao.insert(
                BudgetEntity(
                    userId = userId,
                    bookId = budget.bookId?.let { bookIds[it] },
                    categoryId = budget.categoryId?.let { categoryIds[it] },
                    period = budget.period,
                    amount = budget.amount,
                    isEnabled = budget.isEnabled,
                    createdAt = budget.createdAt,
                ),
            )
        }

        data.recurringRules.forEach { rule ->
            recurringRuleDao.insert(
                RecurringRuleEntity(
                    userId = userId,
                    bookId = requireNotNull(bookIds[rule.bookId]),
                    name = rule.name,
                    type = rule.type,
                    amount = rule.amount,
                    categoryId = rule.categoryId?.let { categoryIds[it] },
                    accountId = requireNotNull(accountIds[rule.accountId]),
                    toAccountId = rule.toAccountId?.let { accountIds[it] },
                    frequency = rule.frequency,
                    interval = rule.interval,
                    anchorDay = rule.anchorDay,
                    remark = rule.remark,
                    autoCreate = rule.autoCreate,
                    isEnabled = rule.isEnabled,
                    nextTriggerAt = rule.nextTriggerAt,
                    createdAt = rule.createdAt,
                ),
            )
        }

        return data.toSummary()
    }

    private fun BackupData.toSummary() = BackupSummary(
        bookCount = books.size,
        accountCount = accounts.size,
        categoryCount = categories.size,
        transactionCount = transactions.size,
        budgetCount = budgets.size,
        recurringRuleCount = recurringRules.size,
    )
}

/**
 * 校验备份内部的引用是否自洽。
 *
 * 导入是一个"先清空再写入"的过程，一旦中途发现引用缺失就只能留下残局。
 * 因此在动数据库之前先把文件本身查清楚。
 */
private fun BackupData.hasConsistentReferences(): Boolean {
    if (books.isEmpty() || accounts.isEmpty()) return false
    // 必须有一本默认账本，否则导入后应用不知道该往哪本账里记
    if (books.none { it.isDefault }) return false

    val bookIds = books.map { it.id }.toSet()
    val accountIds = accounts.map { it.id }.toSet()
    val categoryIds = categories.map { it.id }.toSet()

    val categoriesValid = categories.all { it.parentId == null || it.parentId in categoryIds }
    val transactionsValid = transactions.all {
        it.bookId in bookIds &&
            it.accountId in accountIds &&
            (it.toAccountId == null || it.toAccountId in accountIds) &&
            (it.categoryId == null || it.categoryId in categoryIds)
    }
    val budgetsValid = budgets.all {
        (it.bookId == null || it.bookId in bookIds) &&
            (it.categoryId == null || it.categoryId in categoryIds)
    }
    val rulesValid = recurringRules.all {
        it.bookId in bookIds &&
            it.accountId in accountIds &&
            (it.toAccountId == null || it.toAccountId in accountIds) &&
            (it.categoryId == null || it.categoryId in categoryIds)
    }

    return categoriesValid && transactionsValid && budgetsValid && rulesValid
}

private fun BookEntity.toBackup() = BackupBook(
    id = id,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt,
)

private fun AccountEntity.toBackup() = BackupAccount(
    id = id,
    name = name,
    icon = icon,
    color = color,
    type = type,
    initialBalance = initialBalance,
    includeInTotal = includeInTotal,
    cardTailNumber = cardTailNumber,
    remark = remark,
    sortOrder = sortOrder,
    isArchived = isArchived,
    createdAt = createdAt,
)

private fun CategoryEntity.toBackup() = BackupCategory(
    id = id,
    parentId = parentId,
    name = name,
    icon = icon,
    color = color,
    type = type,
    sortOrder = sortOrder,
    isPreset = isPreset,
    isHidden = isHidden,
)

private fun TransactionEntity.toBackup() = BackupTransaction(
    id = id,
    bookId = bookId,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = toAccountId,
    transferFee = transferFee,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    remark = remark,
    payee = payee,
    excludedFromStats = excludedFromStats,
)

private fun BudgetEntity.toBackup() = BackupBudget(
    id = id,
    bookId = bookId,
    categoryId = categoryId,
    period = period,
    amount = amount,
    isEnabled = isEnabled,
    createdAt = createdAt,
)

private fun RecurringRuleEntity.toBackup() = BackupRecurringRule(
    id = id,
    bookId = bookId,
    name = name,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = toAccountId,
    frequency = frequency,
    interval = interval,
    anchorDay = anchorDay,
    remark = remark,
    autoCreate = autoCreate,
    isEnabled = isEnabled,
    nextTriggerAt = nextTriggerAt,
    createdAt = createdAt,
)
