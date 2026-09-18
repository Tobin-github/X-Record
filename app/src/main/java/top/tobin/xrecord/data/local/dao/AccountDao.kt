package top.tobin.xrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.entity.AccountEntity

/** 账户及其当前余额（单位：分）。 */
data class AccountWithBalance(
    @Embedded val account: AccountEntity,
    val balance: Long,
)

@Dao
interface AccountDao {

    /**
     * 余额计算公式：初始余额 + 该账户的所有流水影响。
     *
     * - 收入：`+amount`
     * - 支出：`-amount`
     * - 转出：`-(amount + 手续费)`
     * - 转入：`+amount`
     *
     * 用相关子查询而非 LEFT JOIN + GROUP BY，避免多表连接时重复计数，也更容易逐条核对。
     */
    @Query(
        """
        SELECT a.*,
               a.initialBalance + COALESCE((
                   SELECT SUM(
                       CASE
                           WHEN t.type = 'INCOME' THEN t.amount
                           WHEN t.type = 'EXPENSE' THEN -t.amount
                           WHEN t.type = 'TRANSFER' AND t.accountId = a.id
                               THEN -(t.amount + COALESCE(t.transferFee, 0))
                           WHEN t.type = 'TRANSFER' AND t.toAccountId = a.id THEN t.amount
                           ELSE 0
                       END
                   )
                   FROM transactions t
                   WHERE t.accountId = a.id OR t.toAccountId = a.id
               ), 0) AS balance
        FROM accounts a
        WHERE a.userId = :userId
        ORDER BY a.isArchived ASC, a.sortOrder ASC, a.id ASC
        """,
    )
    fun observeAccountsWithBalance(userId: Long): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    suspend fun findAccounts(userId: Long): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun findById(accountId: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE userId = :userId")
    suspend fun countForUser(userId: Long): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET isArchived = :archived WHERE id = :accountId")
    suspend fun setArchived(accountId: Long, archived: Boolean)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteById(accountId: Long)

    @Query("DELETE FROM accounts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
