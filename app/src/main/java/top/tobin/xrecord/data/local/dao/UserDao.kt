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

/** 账号名下的数据规模，用于删除前的确认提示。 */
data class AccountDataSummary(
    val transactionCount: Int,
    val bookCount: Int,
    val accountCount: Int,
    val categoryCount: Int,
) {
    val isNotEmpty: Boolean
        get() = transactionCount > 0 || bookCount > 0 || accountCount > 0 || categoryCount > 0
}

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

    /**
     * 删除账号。其名下的账本、账户、分类、流水、标签、预算与定期账单
     * 依靠外键级联一并清除——这是删除账号唯一可靠的实现方式，
     * 逐表删除既容易遗漏，也无法保证原子性。
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: Long)

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM transactions WHERE userId = :userId) AS transactionCount,
            (SELECT COUNT(*) FROM books WHERE userId = :userId) AS bookCount,
            (SELECT COUNT(*) FROM accounts WHERE userId = :userId) AS accountCount,
            (SELECT COUNT(*) FROM categories WHERE userId = :userId) AS categoryCount
        """,
    )
    suspend fun dataSummary(userId: Long): AccountDataSummary
}
