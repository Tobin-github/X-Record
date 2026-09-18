package top.tobin.xrecord.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 流水，核心表。
 *
 * 两个关键约定：
 * 1. `amount` 恒为正数，收支方向由 [type] 决定，避免出现"负数支出"这类歧义数据。
 * 2. `occurredAt`（发生时间）与 `createdAt`（创建时间）必须分开：补记账时二者不同，
 *    所有统计与排序一律使用 `occurredAt`。
 */
@Entity(
    tableName = "transactions",
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
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            // 用 NO ACTION 而非 RESTRICT：约束在语句结束时才校验，因此"删除整个用户"这类
            // 级联删除可以顺利完成，而单独删除一个仍有流水的账户依然会被拒绝。
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["bookId"]),
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
        Index(value = ["toAccountId"]),
        Index(value = ["recurringRuleId"]),
        Index(value = ["userId", "bookId", "occurredAt"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val bookId: Long,
    val type: TransactionType,
    /** 金额，单位：分，恒为正数。 */
    val amount: Long,
    /** 转账时为 null。 */
    val categoryId: Long? = null,
    /** 支出/收入的所属账户；转账的转出账户。 */
    val accountId: Long,
    /** 仅转账使用：转入账户。 */
    val toAccountId: Long? = null,
    /** 仅转账使用：手续费，单位：分。 */
    val transferFee: Long? = null,
    /** 发生时间，统计与排序依据。 */
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val remark: String? = null,
    val payee: String? = null,
    /** 不计入收支统计，例如待报销支出。 */
    val excludedFromStats: Boolean = false,
    /** 由定期账单生成时记录来源。 */
    val recurringRuleId: Long? = null,
)
