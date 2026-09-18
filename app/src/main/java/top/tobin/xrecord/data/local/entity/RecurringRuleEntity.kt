package top.tobin.xrecord.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

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
    /**
     * 锚定日（1~31），表示规则本意的"每月几号"。
     *
     * 不能只靠 nextTriggerAt 递推：1 月 31 日往后推一个月会被钳到 2 月 28 日，
     * 再推就变成 3 月 28 日，规则会一路漂移。记住锚定日才能在短月钳位、
     * 到大月重新回到 31 日。
     *
     * 必须显式声明 `defaultValue`：迁移是通过 `ALTER TABLE ... ADD COLUMN`
     * 加的这一列，Room 会拿"实体期望的表结构"和"迁移后的实际结构"比对，
     * 两边都带 `DEFAULT 1` 才能通过校验。
     */
    @ColumnInfo(defaultValue = "1")
    val anchorDay: Int = 1,
    val remark: String? = null,
    val autoCreate: Boolean = false,
    val isEnabled: Boolean = true,
    val nextTriggerAt: Long,
    val createdAt: Long,
)
