package top.tobin.xrecord.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地账号。
 *
 * 密码只保存 PBKDF2 派生结果，绝不落库明文。`passwordIterations` 单独存储，
 * 便于后续提升迭代次数时仍能校验老密码。
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val username: String,
    val nickname: String,
    val avatarPath: String? = null,
    val passwordHash: String,
    val passwordSalt: String,
    val passwordIterations: Int,
    val createdAt: Long,
    val lastLoginAt: Long? = null,
)
