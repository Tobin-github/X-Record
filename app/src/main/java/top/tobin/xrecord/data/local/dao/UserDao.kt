package top.tobin.xrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.entity.UserEntity

/**
 * 账号选择器需要的展示信息。
 *
 * 刻意不包含密码哈希与盐：它们是数据层的内部细节，没有任何理由流到界面层。
 */
data class LocalAccount(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatarPath: String?,
    val lastLoginAt: Long?,
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun findById(userId: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeById(userId: Long): Flow<UserEntity?>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT * FROM users ORDER BY lastLoginAt DESC, id ASC")
    suspend fun findAll(): List<UserEntity>

    /** 本机已注册的账号，最近登录的排在前面。 */
    @Query(
        """
        SELECT id, username, nickname, avatarPath, lastLoginAt FROM users
        ORDER BY lastLoginAt DESC, id ASC
        """,
    )
    fun observeLocalAccounts(): Flow<List<LocalAccount>>

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE id = :userId")
    suspend fun touchLastLogin(userId: Long, timestamp: Long)

    @Delete
    suspend fun delete(user: UserEntity)
}
