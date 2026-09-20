package top.tobin.xrecord.ui.preview

import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.dao.LocalAccount
import top.tobin.xrecord.data.local.dao.TransactionDetail
import top.tobin.xrecord.data.local.entity.AccountEntity
import top.tobin.xrecord.data.local.entity.AccountType
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.local.entity.BudgetEntity
import top.tobin.xrecord.data.local.entity.BudgetPeriod
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType
import top.tobin.xrecord.data.local.entity.RecurringFrequency
import top.tobin.xrecord.data.local.entity.RecurringRuleEntity
import top.tobin.xrecord.data.local.entity.TransactionEntity
import top.tobin.xrecord.data.local.entity.TransactionType

/**
 * 预览用的示例数据。
 *
 * 集中放一份而不是散在各页面的 `@Preview` 里：示例值保持一致，
 * 改一处就能同步到所有预览，也避免每个预览各自编一套假数据。
 *
 * 数值刻意挑得有代表性——金额有大有小、分类覆盖常见场景、
 * 账户有正有负，这样预览里能看出真实的排版效果。
 */
object PreviewData {

    private const val USER_ID = 1L
    private const val BOOK_ID = 1L

    private fun timestamp(daysAgo: Long, hour: Int = 12, minute: Int = 30): Long {
        val now = System.currentTimeMillis()
        return now - daysAgo * 24 * 60 * 60 * 1000L + hour * 60 * 60 * 1000L +
            minute * 60 * 1000L
    }

    fun category(
        id: Long,
        name: String,
        color: String,
        type: CategoryType = CategoryType.EXPENSE,
    ) = CategoryEntity(
        id = id,
        userId = USER_ID,
        parentId = null,
        name = name,
        icon = "preview",
        color = color,
        type = type,
        sortOrder = id.toInt(),
        isPreset = true,
        isHidden = false,
    )

    val expenseCategories = listOf(
        category(1, "餐饮", "#FF7043"),
        category(2, "交通", "#42A5F5"),
        category(3, "购物", "#EC407A"),
        category(4, "居住", "#8D6E63"),
        category(5, "通讯", "#26A69A"),
        category(6, "娱乐", "#AB47BC"),
        category(7, "医疗", "#EF5350"),
        category(8, "教育", "#5C6BC0"),
        category(9, "人情", "#FFA726"),
        category(10, "旅行", "#29B6F6"),
    )

    val incomeCategories = listOf(
        category(21, "工资", "#43A047", CategoryType.INCOME),
        category(22, "奖金", "#FBC02D", CategoryType.INCOME),
        category(23, "兼职", "#00897B", CategoryType.INCOME),
        category(24, "投资", "#1E88E5", CategoryType.INCOME),
        category(25, "红包", "#E53935", CategoryType.INCOME),
    )

    val allCategories = expenseCategories + incomeCategories

    fun account(
        id: Long,
        name: String,
        type: AccountType,
        color: String,
        balance: Long,
    ) = AccountWithBalance(
        account = AccountEntity(
            id = id,
            userId = USER_ID,
            name = name,
            icon = "preview",
            color = color,
            type = type,
            initialBalance = balance,
            includeInTotal = type != AccountType.CREDIT_CARD,
            cardTailNumber = null,
            remark = null,
            sortOrder = id.toInt(),
            isArchived = false,
            createdAt = timestamp(120),
        ),
        balance = balance,
    )

    val accounts = listOf(
        account(1, "现金", AccountType.CASH, "#43A047", 320_50),
        account(2, "招行卡", AccountType.DEBIT_CARD, "#1E88E5", 12_480_00),
        account(3, "支付宝", AccountType.ALIPAY, "#0288D1", 2_150_75),
        account(4, "信用卡", AccountType.CREDIT_CARD, "#8E24AA", -3_820_00),
    )

    fun transaction(
        id: Long,
        type: TransactionType,
        amount: Long,
        categoryId: Long?,
        accountId: Long,
        toAccountId: Long? = null,
        remark: String? = null,
        daysAgo: Long = 0,
        hour: Int = 12,
    ) = TransactionEntity(
        id = id,
        userId = USER_ID,
        bookId = BOOK_ID,
        type = type,
        amount = amount,
        categoryId = categoryId,
        accountId = accountId,
        toAccountId = toAccountId,
        transferFee = null,
        occurredAt = timestamp(daysAgo, hour),
        createdAt = timestamp(daysAgo, hour),
        updatedAt = timestamp(daysAgo, hour),
        remark = remark,
        payee = null,
        excludedFromStats = false,
        recurringRuleId = null,
    )

    private fun detail(
        transaction: TransactionEntity,
        category: CategoryEntity? = null,
        accountName: String = "现金",
        toAccountName: String? = null,
    ) = TransactionDetail(
        transaction = transaction,
        categoryName = category?.name,
        categoryIcon = category?.icon,
        categoryColor = category?.color,
        accountName = accountName,
        toAccountName = toAccountName,
    )

    val transactionDetails = listOf(
        detail(
            transaction(1, TransactionType.EXPENSE, 3_500, 1, 1, remark = "公司楼下的面", hour = 12),
            expenseCategories[0],
        ),
        detail(
            transaction(2, TransactionType.EXPENSE, 1_200, 2, 3, remark = "地铁", hour = 9),
            expenseCategories[1],
            accountName = "支付宝",
        ),
        detail(
            transaction(3, TransactionType.INCOME, 1_850_000, 21, 2, remark = "九月工资", hour = 10),
            incomeCategories[0],
            accountName = "招行卡",
        ),
        detail(
            transaction(4, TransactionType.TRANSFER, 200_000, null, 2, toAccountId = 3, hour = 8),
            accountName = "招行卡",
            toAccountName = "支付宝",
        ),
        detail(
            transaction(5, TransactionType.EXPENSE, 12_800, 3, 3, remark = "日用品", daysAgo = 1, hour = 20),
            expenseCategories[2],
            accountName = "支付宝",
        ),
        detail(
            transaction(6, TransactionType.EXPENSE, 2_800_00, 4, 2, remark = "房租", daysAgo = 2, hour = 9),
            expenseCategories[3],
            accountName = "招行卡",
        ),
    )

    val books = listOf(
        BookEntity(1, USER_ID, "日常账本", "book", 0, true, timestamp(120)),
        BookEntity(2, USER_ID, "旅行账本", "book", 1, false, timestamp(30)),
    )

    fun budget(categoryId: Long?, amount: Long, id: Long) = BudgetEntity(
        id = id,
        userId = USER_ID,
        bookId = null,
        categoryId = categoryId,
        period = BudgetPeriod.MONTH,
        amount = amount,
        isEnabled = true,
        createdAt = timestamp(60),
    )

    val budgets = listOf(
        budget(null, 6_000_00, 1),
        budget(1, 1_200_00, 2),
        budget(2, 300_00, 3),
    )

    fun recurringRule(
        id: Long,
        name: String,
        amount: Long,
        categoryId: Long?,
        frequency: RecurringFrequency,
        autoCreate: Boolean = true,
        enabled: Boolean = true,
    ) = RecurringRuleEntity(
        id = id,
        userId = USER_ID,
        bookId = BOOK_ID,
        name = name,
        type = TransactionType.EXPENSE,
        amount = amount,
        categoryId = categoryId,
        accountId = 2,
        toAccountId = null,
        frequency = frequency,
        interval = 1,
        anchorDay = 10,
        remark = null,
        autoCreate = autoCreate,
        isEnabled = enabled,
        nextTriggerAt = timestamp(-9),
        createdAt = timestamp(90),
    )

    val recurringRules = listOf(
        recurringRule(1, "房租", 2_800_00, 4, RecurringFrequency.MONTHLY),
        recurringRule(2, "视频会员", 25_00, 6, RecurringFrequency.MONTHLY, enabled = false),
        recurringRule(3, "工资", 1_850_000, 21, RecurringFrequency.MONTHLY),
    )

    val localAccounts = listOf(
        LocalAccount(1, "tobin", "Tobin", null, timestamp(0)),
        LocalAccount(2, "leo", "Leo", null, timestamp(3)),
    )
}
