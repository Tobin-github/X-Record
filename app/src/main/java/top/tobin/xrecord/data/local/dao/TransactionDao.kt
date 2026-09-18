package top.tobin.xrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.entity.TransactionEntity
import top.tobin.xrecord.data.local.entity.TransactionType

/** 某个分类在区间内的支出/收入合计。 */
data class CategorySum(
    val categoryId: Long?,
    val total: Long,
)

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun findById(transactionId: Long): TransactionEntity?

    /**
     * 按发生时间倒序查询区间内的流水。
     *
     * 区间统一采用左闭右开 `[startInclusive, endExclusive)`，避免边界时间被重复统计。
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE userId = :userId
          AND bookId = :bookId
          AND occurredAt >= :startInclusive
          AND occurredAt < :endExclusive
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun observeInRange(
        userId: Long,
        bookId: Long,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<TransactionEntity>>

    /**
     * 区间内的收支合计。转账被显式排除，`excludedFromStats` 的流水也不计入。
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE userId = :userId
          AND bookId = :bookId
          AND type = :type
          AND excludedFromStats = 0
          AND occurredAt >= :startInclusive
          AND occurredAt < :endExclusive
        """,
    )
    fun observeSumByType(
        userId: Long,
        bookId: Long,
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<Long>

    /** 区间内按分类聚合，用于图表页的分类占比与排行。 */
    @Query(
        """
        SELECT categoryId, SUM(amount) AS total FROM transactions
        WHERE userId = :userId
          AND bookId = :bookId
          AND type = :type
          AND excludedFromStats = 0
          AND categoryId IS NOT NULL
          AND occurredAt >= :startInclusive
          AND occurredAt < :endExclusive
        GROUP BY categoryId
        ORDER BY total DESC
        """,
    )
    fun observeCategorySums(
        userId: Long,
        bookId: Long,
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<CategorySum>>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE accountId = :accountId OR toAccountId = :accountId
        """,
    )
    suspend fun countForAccount(accountId: Long): Int
}
