package top.tobin.xrecord.data.backup

import kotlinx.serialization.Serializable
import top.tobin.xrecord.data.local.entity.AccountType
import top.tobin.xrecord.data.local.entity.BudgetPeriod
import top.tobin.xrecord.data.local.entity.CategoryType
import top.tobin.xrecord.data.local.entity.RecurringFrequency
import top.tobin.xrecord.data.local.entity.TransactionType

/**
 * 备份文件。
 *
 * 刻意**不含用户名与密码哈希**：备份会被放到用户自己的云盘或电脑上，
 * 带上凭据等于把账号口令散落到应用之外。恢复时数据落到当前登录的账号下。
 *
 * 各实体的 id 原样保留，导入时再重映射——id 在这个库里是全局自增的，
 * 直接沿用会和目标账号的既有记录撞主键。
 */
@Serializable
data class BackupData(
    val formatVersion: Int,
    val exportedAt: Long,
    val appVersionName: String,
    val books: List<BackupBook>,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val budgets: List<BackupBudget>,
    val recurringRules: List<BackupRecurringRule>,
) {
    companion object {
        /** 当前备份格式版本。导入时据此判断能否读取。 */
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class BackupBook(
    val id: Long,
    val name: String,
    val icon: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val createdAt: Long,
)

@Serializable
data class BackupAccount(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val type: AccountType,
    val initialBalance: Long,
    val includeInTotal: Boolean,
    val cardTailNumber: String?,
    val remark: String?,
    val sortOrder: Int,
    val isArchived: Boolean,
    val createdAt: Long,
)

@Serializable
data class BackupCategory(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val icon: String,
    val color: String,
    val type: CategoryType,
    val sortOrder: Int,
    val isPreset: Boolean,
    val isHidden: Boolean,
)

@Serializable
data class BackupTransaction(
    val id: Long,
    val bookId: Long,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long?,
    val transferFee: Long?,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val remark: String?,
    val payee: String?,
    val excludedFromStats: Boolean,
)

@Serializable
data class BackupBudget(
    val id: Long,
    val bookId: Long?,
    val categoryId: Long?,
    val period: BudgetPeriod,
    val amount: Long,
    val isEnabled: Boolean,
    val createdAt: Long,
)

@Serializable
data class BackupRecurringRule(
    val id: Long,
    val bookId: Long,
    val name: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long?,
    val frequency: RecurringFrequency,
    val interval: Int,
    val anchorDay: Int,
    val remark: String?,
    val autoCreate: Boolean,
    val isEnabled: Boolean,
    val nextTriggerAt: Long,
    val createdAt: Long,
)

/** 导入结果。 */
sealed interface ImportResult {
    data class Success(val summary: BackupSummary) : ImportResult

    /** 文件本身读不懂（格式不对、版本不支持、JSON 损坏）。 */
    data class InvalidFormat(val reason: ImportError) : ImportResult

    /** 文件结构正确但内部引用不一致，例如流水指向了不存在的账户。 */
    data object BrokenReferences : ImportResult
}

enum class ImportError {
    UNSUPPORTED_VERSION,
    MALFORMED,
}

data class BackupSummary(
    val bookCount: Int,
    val accountCount: Int,
    val categoryCount: Int,
    val transactionCount: Int,
    val budgetCount: Int,
    val recurringRuleCount: Int,
)
