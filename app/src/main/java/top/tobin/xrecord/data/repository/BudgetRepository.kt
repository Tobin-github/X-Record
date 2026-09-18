package top.tobin.xrecord.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.data.local.dao.BudgetDao
import top.tobin.xrecord.data.local.entity.BudgetEntity
import top.tobin.xrecord.data.local.entity.BudgetPeriod

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeBudgets(userId: Long, bookId: Long): Flow<List<BudgetEntity>> =
        budgetDao.observeBudgets(userId, bookId)

    /**
     * 设置预算。
     *
     * 同一「用户 + 分类 + 周期」只保留一条记录：已存在就改额度而不是再插一条，
     * 否则同一分类会出现多个互相矛盾的预算。
     */
    suspend fun setBudget(
        userId: Long,
        bookId: Long,
        categoryId: Long?,
        amount: Long,
        period: BudgetPeriod = BudgetPeriod.MONTH,
    ) = withContext(ioDispatcher) {
        val existing = budgetDao.find(userId, bookId, categoryId, period)
        if (existing == null) {
            budgetDao.insert(
                BudgetEntity(
                    userId = userId,
                    // 预算跟着账本走：切到另一本账时，看到的是那本账自己的预算
                    bookId = bookId,
                    categoryId = categoryId,
                    period = period,
                    amount = amount,
                    isEnabled = true,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        } else {
            budgetDao.update(existing.copy(amount = amount, isEnabled = true))
        }
    }

    suspend fun removeBudget(budget: BudgetEntity) = withContext(ioDispatcher) {
        budgetDao.delete(budget)
    }
}
