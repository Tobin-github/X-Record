package top.tobin.xrecord.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.data.local.dao.AccountDao
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.entity.AccountEntity
import top.tobin.xrecord.data.local.entity.AccountType

/** 新建或编辑账户时用户填写的内容。 */
data class AccountDraft(
    val name: String,
    val type: AccountType,
    val initialBalance: Long,
    val includeInTotal: Boolean,
)

enum class AccountError {
    NAME_EMPTY,
    HAS_TRANSACTIONS,
    LAST_ACCOUNT,
    UNKNOWN,
}

sealed interface AccountResult {
    data object Success : AccountResult

    data class Failure(val error: AccountError) : AccountResult
}

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeAccounts(userId: Long): Flow<List<AccountWithBalance>> =
        accountDao.observeAccountsWithBalance(userId)

    suspend fun create(userId: Long, draft: AccountDraft): AccountResult =
        withContext(ioDispatcher) {
            val name = draft.name.trim()
            if (name.isEmpty()) return@withContext AccountResult.Failure(AccountError.NAME_EMPTY)

            val existing = accountDao.findAccounts(userId)
            accountDao.insert(
                AccountEntity(
                    userId = userId,
                    name = name,
                    icon = draft.type.defaultIcon(),
                    color = draft.type.defaultColor(),
                    type = draft.type,
                    initialBalance = draft.initialBalance,
                    includeInTotal = draft.includeInTotal,
                    sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                    isArchived = false,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            AccountResult.Success
        }

    suspend fun update(account: AccountEntity, draft: AccountDraft): AccountResult =
        withContext(ioDispatcher) {
            val name = draft.name.trim()
            if (name.isEmpty()) return@withContext AccountResult.Failure(AccountError.NAME_EMPTY)

            accountDao.update(
                account.copy(
                    name = name,
                    type = draft.type,
                    // 类型变了，图标和配色也跟着换，避免出现"信用卡配现金图标"
                    icon = draft.type.defaultIcon(),
                    color = draft.type.defaultColor(),
                    initialBalance = draft.initialBalance,
                    includeInTotal = draft.includeInTotal,
                ),
            )
            AccountResult.Success
        }

    suspend fun setArchived(accountId: Long, archived: Boolean) = withContext(ioDispatcher) {
        accountDao.setArchived(accountId, archived)
    }

    /**
     * 删除账户。
     *
     * 只有在没有任何流水引用、且删完还剩至少一个可用账户时才允许。
     * 否则要么历史流水失去归属，要么用户下次记账时无账户可选。
     */
    suspend fun delete(userId: Long, account: AccountEntity): AccountResult =
        withContext(ioDispatcher) {
            if (transactionDao.countForAccount(account.id) > 0) {
                return@withContext AccountResult.Failure(AccountError.HAS_TRANSACTIONS)
            }
            val remaining = accountDao.findAccounts(userId)
                .count { it.id != account.id && !it.isArchived }
            if (remaining == 0) {
                return@withContext AccountResult.Failure(AccountError.LAST_ACCOUNT)
            }

            accountDao.deleteById(account.id)
            AccountResult.Success
        }
}

/**
 * 账户类型决定默认图标与配色。
 *
 * 存的是语义化键而不是资源 id，UI 层负责映射成具体样式。
 */
fun AccountType.defaultIcon(): String = when (this) {
    AccountType.CASH -> "cash"
    AccountType.DEBIT_CARD -> "card"
    AccountType.CREDIT_CARD -> "credit_card"
    AccountType.ALIPAY -> "alipay"
    AccountType.WECHAT -> "wechat"
    AccountType.INVESTMENT -> "investment"
    AccountType.DEBT -> "debt"
    AccountType.OTHER -> "wallet"
}

fun AccountType.defaultColor(): String = when (this) {
    AccountType.CASH -> "#43A047"
    AccountType.DEBIT_CARD -> "#1E88E5"
    AccountType.CREDIT_CARD -> "#8E24AA"
    AccountType.ALIPAY -> "#0288D1"
    AccountType.WECHAT -> "#2E7D32"
    AccountType.INVESTMENT -> "#F57C00"
    AccountType.DEBT -> "#E53935"
    AccountType.OTHER -> "#546E7A"
}

/** 信用卡、负债类账户默认不计入总资产，否则总资产会被负债撑高。 */
fun AccountType.defaultIncludeInTotal(): Boolean = when (this) {
    AccountType.CREDIT_CARD, AccountType.DEBT -> false
    else -> true
}
