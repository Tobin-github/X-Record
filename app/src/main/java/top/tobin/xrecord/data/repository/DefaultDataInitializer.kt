package top.tobin.xrecord.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import top.tobin.xrecord.data.local.dao.AccountDao
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.entity.AccountEntity
import top.tobin.xrecord.data.local.entity.AccountType
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType

/**
 * 为新账号准备可立即使用的数据。
 *
 * 必须由调用方放在数据库事务里执行：新用户要么完整拥有一套账本、账户和分类，
 * 要么什么都没有。中途失败留下"有账本但没分类"的半成品，会让用户第一次记账就卡住。
 */
@Singleton
class DefaultDataInitializer @Inject constructor(
    private val bookDao: BookDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) {

    /**
     * @return 新建的默认账本 id
     */
    suspend fun createDefaults(userId: Long, now: Long): Long {
        val bookId = bookDao.insert(
            BookEntity(
                userId = userId,
                name = PresetData.DEFAULT_BOOK_NAME,
                icon = "book",
                sortOrder = 0,
                isDefault = true,
                createdAt = now,
            ),
        )

        accountDao.insert(
            AccountEntity(
                userId = userId,
                name = PresetData.DEFAULT_ACCOUNT_NAME,
                icon = "cash",
                color = "#43A047",
                type = AccountType.CASH,
                initialBalance = 0L,
                includeInTotal = true,
                sortOrder = 0,
                createdAt = now,
            ),
        )

        categoryDao.insertAll(
            PresetData.expenseCategories.mapIndexed { index, preset ->
                preset.toEntity(userId, CategoryType.EXPENSE, index)
            } + PresetData.incomeCategories.mapIndexed { index, preset ->
                preset.toEntity(userId, CategoryType.INCOME, index)
            },
        )

        return bookId
    }

    private fun PresetData.PresetCategory.toEntity(
        userId: Long,
        type: CategoryType,
        sortOrder: Int,
    ) = CategoryEntity(
        userId = userId,
        parentId = null,
        name = name,
        icon = icon,
        color = color,
        type = type,
        sortOrder = sortOrder,
        isPreset = true,
        isHidden = false,
    )
}
