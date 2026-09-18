package top.tobin.xrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.entity.RecurringRuleEntity

@Dao
interface RecurringRuleDao {
    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE userId = :userId
        ORDER BY isEnabled DESC, nextTriggerAt ASC, id ASC
        """,
    )
    fun observeRules(userId: Long): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE userId = :userId ORDER BY id ASC")
    suspend fun findAllForUser(userId: Long): List<RecurringRuleEntity>

    /**
     * 到期待生成的规则。
     *
     * 只取 autoCreate 的：仅提醒的规则不该悄悄产生流水。
     */
    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE userId = :userId AND isEnabled = 1 AND autoCreate = 1 AND nextTriggerAt <= :now
        ORDER BY nextTriggerAt ASC
        """,
    )
    suspend fun findDue(userId: Long, now: Long): List<RecurringRuleEntity>

    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :ruleId")
    suspend fun deleteById(ruleId: Long)

    @Query("DELETE FROM recurring_rules WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
