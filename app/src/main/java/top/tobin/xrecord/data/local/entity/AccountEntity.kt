package top.tobin.xrecord.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 资产账户，归属用户而非账本：同一张银行卡在多本账之间共用，无需重复创建。
 *
 * 账户不做物理删除，只归档（[isArchived]），否则历史流水会失去引用。
 */
@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId"])],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val name: String,
    val icon: String,
    val color: String,
    val type: AccountType,
    /** 初始余额，单位：分。信用卡、负债类账户可为负。 */
    val initialBalance: Long = 0L,
    /** 是否计入总资产，贷款类账户通常关闭。 */
    val includeInTotal: Boolean = true,
    val cardTailNumber: String? = null,
    val remark: String? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
)
