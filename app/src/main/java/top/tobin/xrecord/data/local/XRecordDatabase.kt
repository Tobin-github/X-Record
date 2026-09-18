package top.tobin.xrecord.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import top.tobin.xrecord.data.local.dao.AccountDao
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.BudgetDao
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.dao.UserDao
import top.tobin.xrecord.data.local.dao.RecurringRuleDao
import top.tobin.xrecord.data.local.entity.AccountEntity
import top.tobin.xrecord.data.local.entity.BookEntity
import top.tobin.xrecord.data.local.entity.BudgetEntity
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.Converters
import top.tobin.xrecord.data.local.entity.RecurringRuleEntity
import top.tobin.xrecord.data.local.entity.TagEntity
import top.tobin.xrecord.data.local.entity.TransactionEntity
import top.tobin.xrecord.data.local.entity.TransactionTagCrossRef
import top.tobin.xrecord.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        BookEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class XRecordDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    abstract fun bookDao(): BookDao

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun budgetDao(): BudgetDao

    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        const val NAME = "x-record.db"
    }
}

/**
 * 数据库迁移。
 *
 * 这里的每一条都对应一次表结构变更，禁止使用破坏性回退——用户的数据就是全部资产。
 */
object XRecordMigrations {

    /** 定期账单增加"锚定日"，用于「每月 N 号」的正确递推。 */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE recurring_rules ADD COLUMN anchorDay INTEGER NOT NULL DEFAULT 1",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
