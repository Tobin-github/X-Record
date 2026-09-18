package top.tobin.xrecord.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType

enum class CategoryError {
    NAME_EMPTY,
    PRESET_CANNOT_DELETE,
    IN_USE,
    HAS_CHILDREN,
    UNKNOWN,
}

sealed interface CategoryResult {
    data object Success : CategoryResult

    data class Failure(val error: CategoryError) : CategoryResult
}

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeCategories(userId: Long, type: CategoryType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(userId, type)

    suspend fun create(
        userId: Long,
        type: CategoryType,
        name: String,
        parentId: Long? = null,
    ): CategoryResult = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext CategoryResult.Failure(CategoryError.NAME_EMPTY)

        val siblings = categoryDao.findByType(userId, type)
        categoryDao.insert(
            CategoryEntity(
                userId = userId,
                parentId = parentId,
                name = trimmed,
                icon = "custom",
                color = CUSTOM_COLORS[siblings.size % CUSTOM_COLORS.size],
                type = type,
                sortOrder = (siblings.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                isPreset = false,
                isHidden = false,
            ),
        )
        CategoryResult.Success
    }

    suspend fun rename(category: CategoryEntity, name: String): CategoryResult =
        withContext(ioDispatcher) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@withContext CategoryResult.Failure(CategoryError.NAME_EMPTY)
            categoryDao.update(category.copy(name = trimmed))
            CategoryResult.Success
        }

    suspend fun setHidden(categoryId: Long, hidden: Boolean) = withContext(ioDispatcher) {
        categoryDao.setHidden(categoryId, hidden)
    }

    /**
     * 删除分类。
     *
     * 预置分类只能隐藏：它们是统计口径的一部分，删掉会让历史流水与图表对不上。
     * 自建分类若已有流水或有子分类，也一律拒绝——外键会把流水置为"未分类"、
     * 把子分类级联删除，这两种结果都不该在用户没被告知的情况下发生。
     */
    suspend fun delete(category: CategoryEntity): CategoryResult = withContext(ioDispatcher) {
        if (category.isPreset) {
            return@withContext CategoryResult.Failure(CategoryError.PRESET_CANNOT_DELETE)
        }
        if (categoryDao.countChildren(category.id) > 0) {
            return@withContext CategoryResult.Failure(CategoryError.HAS_CHILDREN)
        }
        if (transactionDao.countForCategory(category.id) > 0) {
            return@withContext CategoryResult.Failure(CategoryError.IN_USE)
        }

        categoryDao.delete(category)
        CategoryResult.Success
    }

    private companion object {
        val CUSTOM_COLORS = listOf(
            "#5C6BC0",
            "#26A69A",
            "#EF5350",
            "#FFA726",
            "#7E57C2",
            "#29B6F6",
        )
    }
}
