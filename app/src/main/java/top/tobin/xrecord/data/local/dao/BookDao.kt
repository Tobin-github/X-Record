package top.tobin.xrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.entity.BookEntity

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE userId = :userId ORDER BY isDefault DESC, sortOrder ASC, id ASC")
    fun observeBooks(userId: Long): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE userId = :userId ORDER BY isDefault DESC, sortOrder ASC, id ASC")
    suspend fun findBooks(userId: Long): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun findById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE userId = :userId AND isDefault = 1 LIMIT 1")
    suspend fun findDefault(userId: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("UPDATE books SET isDefault = 0 WHERE userId = :userId")
    suspend fun clearDefault(userId: Long)
}
