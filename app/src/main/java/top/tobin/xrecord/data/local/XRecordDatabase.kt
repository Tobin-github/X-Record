package top.tobin.xrecord.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import top.tobin.xrecord.data.local.dao.AccountDao
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.dao.UserDao
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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class XRecordDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    abstract fun bookDao(): BookDao

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "x-record.db"
    }
}
