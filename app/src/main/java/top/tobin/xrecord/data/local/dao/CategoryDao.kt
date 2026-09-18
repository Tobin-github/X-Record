package top.tobin.xrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT * FROM categories
        WHERE userId = :userId AND isHidden = 0
        ORDER BY type ASC, sortOrder ASC, id ASC
        """,
    )
    fun observeVisible(userId: Long): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM categories
        WHERE userId = :userId AND type = :type AND parentId IS NULL AND isHidden = 0
        ORDER BY sortOrder ASC, id ASC
        """,
    )
    fun observeTopLevel(userId: Long, type: CategoryType): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM categories
        WHERE userId = :userId AND type = :type
        ORDER BY sortOrder ASC, id ASC
        """,
    )
    suspend fun findByType(userId: Long, type: CategoryType): List<CategoryEntity>

    /** 管理页需要看到被隐藏的分类，因此不过滤 isHidden。 */
    @Query(
        """
        SELECT * FROM categories
        WHERE userId = :userId AND type = :type
        ORDER BY parentId IS NOT NULL ASC, sortOrder ASC, id ASC
        """,
    )
    fun observeByType(userId: Long, type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun findById(categoryId: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE userId = :userId")
    suspend fun countForUser(userId: Long): Int

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET isHidden = :hidden WHERE id = :categoryId")
    suspend fun setHidden(categoryId: Long, hidden: Boolean)

    /**
     * 只允许删除用户自建分类。预置分类被流水引用时会破坏统计口径，
     * 因此预置分类必须通过 [setHidden] 隐藏。
     */
    @Query("DELETE FROM categories WHERE id = :categoryId AND isPreset = 0")
    suspend fun deleteCustom(categoryId: Long)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
