package top.tobin.xrecord.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import top.tobin.xrecord.data.local.dao.AccountDao
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType

/** 账本、账户、分类等"记账时要用到的参照数据"。 */
@Singleton
class LedgerRepository @Inject constructor(
    private val bookDao: BookDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) {

    fun observeDefaultBook(userId: Long): Flow<BookEntity?> = bookDao.observeDefault(userId)

    fun observeAccounts(userId: Long): Flow<List<AccountWithBalance>> =
        accountDao.observeAccountsWithBalance(userId)

    fun observeCategories(userId: Long, type: CategoryType): Flow<List<CategoryEntity>> =
        categoryDao.observeTopLevel(userId, type)
}
