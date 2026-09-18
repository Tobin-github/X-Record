package top.tobin.xrecord.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 定期账单规则，由 WorkManager 按 [nextTriggerAt] 触发。
 *
 * [autoCreate] 为 true 时自动生成流水，为 false 时只发提醒交给用户确认。
 */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["bookId"]),
        Index(value = ["accountId"]),
    ],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val bookId: Long,
    val name: String,
    val type: TransactionType,
    /** 金额，单位：分。 */
    val amount: Long,
    val categoryId: Long? = null,
    val accountId: Long,
    val toAccountId: Long? = null,
    val frequency: RecurringFrequency,
    /** 间隔倍数，例如 frequency = WEEKLY、interval = 2 表示每两周。 */
    val interval: Int = 1,
    val remark: String? = null,
    val autoCreate: Boolean = false,
    val isEnabled: Boolean = true,
    val nextTriggerAt: Long,
    val createdAt: Long,
)
