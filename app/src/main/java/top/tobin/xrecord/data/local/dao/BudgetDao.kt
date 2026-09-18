package top.tobin.xrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.entity.BudgetEntity
import top.tobin.xrecord.data.local.entity.BudgetPeriod

@Dao
interface BudgetDao {
    /** `categoryId` 为 null 的是总预算，排在前面。 */
    @Query(
        """
        SELECT * FROM budgets
        WHERE userId = :userId AND bookId = :bookId AND isEnabled = 1
        ORDER BY categoryId IS NOT NULL ASC, id ASC
        """,
    )
    fun observeBudgets(userId: Long, bookId: Long): Flow<List<BudgetEntity>>

    /** 用 `IS` 而不是 `=`，这样 `categoryId` 为 null 的总预算也能匹配到。 */
    @Query(
        """
        SELECT * FROM budgets
        WHERE userId = :userId AND bookId = :bookId
          AND categoryId IS :categoryId AND period = :period
        LIMIT 1
        """,
    )
    suspend fun find(
        userId: Long,
        bookId: Long,
        categoryId: Long?,
        period: BudgetPeriod,
    ): BudgetEntity?

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY id ASC")
    suspend fun findAllForUser(userId: Long): List<BudgetEntity>

    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
