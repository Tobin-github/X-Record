package top.tobin.xrecord.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.data.local.XRecordDatabase
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.preferences.SettingsDataSource

enum class BookError {
    NAME_EMPTY,
    LAST_BOOK,
    UNKNOWN,
}

sealed interface BookResult {
    data class Success(val bookId: Long) : BookResult

    data class Failure(val error: BookError) : BookResult
}

@Singleton
class BookRepository @Inject constructor(
    private val database: XRecordDatabase,
    private val bookDao: BookDao,
    private val transactionDao: TransactionDao,
    private val settings: SettingsDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeBooks(userId: Long): Flow<List<BookEntity>> = bookDao.observeBooks(userId)

    /**
     * 当前生效账本的一次性快照，供写入流水时使用。
     *
     * 与 [observeCurrentBook] 共用同一套回落规则，避免"看的是一本、写的却是另一本"。
     */
    suspend fun resolveCurrentBook(userId: Long): BookEntity? = withContext(ioDispatcher) {
        val selectedId = settings.currentBookId.first()
        selectCurrent(bookDao.findBooks(userId), selectedId)
    }

    /**
     * 当前生效的账本。
     *
     * 用户选中的账本可能已被删除，此时回落到默认账本；默认账本也没了就取第一本，
     * 保证流水查询永远有一个确定的 bookId，不会出现"选中的账本不存在导致列表空白"。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCurrentBook(userId: Long): Flow<BookEntity?> =
        combine(bookDao.observeBooks(userId), settings.currentBookId) { books, selectedId ->
            selectCurrent(books, selectedId)
        }

    /** 用户选中的账本可能已被删除：依次回落到默认账本、第一本账本。 */
    private fun selectCurrent(books: List<BookEntity>, selectedId: Long?): BookEntity? =
        books.firstOrNull { it.id == selectedId }
            ?: books.firstOrNull { it.isDefault }
            ?: books.firstOrNull()

    suspend fun selectBook(bookId: Long) {
        settings.setCurrentBookId(bookId)
    }

    suspend fun countTransactions(bookId: Long): Int =
        withContext(ioDispatcher) { transactionDao.countForBook(bookId) }

    suspend fun createBook(userId: Long, name: String): BookResult = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext BookResult.Failure(BookError.NAME_EMPTY)

        val bookId = bookDao.insert(
            BookEntity(
                userId = userId,
                name = trimmed,
                icon = "book",
                sortOrder = (bookDao.findBooks(userId).maxOfOrNull { it.sortOrder } ?: 0) + 1,
                isDefault = false,
                createdAt = System.currentTimeMillis(),
            ),
        )
        settings.setCurrentBookId(bookId)
        BookResult.Success(bookId)
    }

    suspend fun renameBook(bookId: Long, name: String): BookResult = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext BookResult.Failure(BookError.NAME_EMPTY)

        val book = bookDao.findById(bookId)
            ?: return@withContext BookResult.Failure(BookError.UNKNOWN)
        bookDao.update(book.copy(name = trimmed))
        BookResult.Success(bookId)
    }

    suspend fun setDefaultBook(userId: Long, bookId: Long): BookResult = withContext(ioDispatcher) {
        database.withTransaction {
            bookDao.clearDefault(userId)
            bookDao.findById(bookId)?.let { bookDao.update(it.copy(isDefault = true)) }
        }
        BookResult.Success(bookId)
    }

    /**
     * 删除账本。
     *
     * 账本下的流水通过外键级联一并删除，因此调用方必须先向用户确认流水条数。
     * 最后一本账本不允许删除，否则用户会失去唯一的记账入口。
     */
    suspend fun deleteBook(userId: Long, bookId: Long): BookResult = withContext(ioDispatcher) {
        val books = bookDao.findBooks(userId)
        if (books.size <= 1) {
            return@withContext BookResult.Failure(BookError.LAST_BOOK)
        }

        val target = books.firstOrNull { it.id == bookId }
            ?: return@withContext BookResult.Failure(BookError.UNKNOWN)
        val fallback = books.firstOrNull { it.id != bookId && it.isDefault }
            ?: books.first { it.id != bookId }

        database.withTransaction {
            bookDao.delete(target)
            if (target.isDefault) {
                bookDao.update(fallback.copy(isDefault = true))
            }
        }
        settings.setCurrentBookId(fallback.id)
        BookResult.Success(fallback.id)
    }
}
