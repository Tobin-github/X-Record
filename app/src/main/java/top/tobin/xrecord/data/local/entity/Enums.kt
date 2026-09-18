package top.tobin.xrecord.data.local.entity

/** 资产账户类型。 */
enum class AccountType {
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    ALIPAY,
    WECHAT,
    INVESTMENT,
    DEBT,
    OTHER,
}

/** 分类的收支方向。二级分类继承一级分类的方向。 */
enum class CategoryType {
    EXPENSE,
    INCOME,
}

/**
 * 流水类型。
 *
 * TRANSFER 是独立类型而非特殊的支出/收入：转账不计入收支统计，只改变账户余额分布。
 */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

/** 预算周期。 */
enum class BudgetPeriod {
    MONTH,
    WEEK,
    YEAR,
}

/** 定期账单的重复频率。 */
enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}
