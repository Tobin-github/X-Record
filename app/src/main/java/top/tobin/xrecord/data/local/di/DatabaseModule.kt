package top.tobin.xrecord.data.local.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import top.tobin.xrecord.data.local.XRecordDatabase
import top.tobin.xrecord.data.local.dao.AccountDao
import top.tobin.xrecord.data.local.dao.BookDao
import top.tobin.xrecord.data.local.dao.CategoryDao
import top.tobin.xrecord.data.local.dao.TransactionDao
import top.tobin.xrecord.data.local.dao.UserDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XRecordDatabase =
        Room.databaseBuilder(context, XRecordDatabase::class.java, XRecordDatabase.NAME)
            // 表结构变更必须显式提供 Migration，禁止破坏性回退，见 docs/requirements.md
            .build()

    @Provides
    fun provideUserDao(database: XRecordDatabase): UserDao = database.userDao()

    @Provides
    fun provideBookDao(database: XRecordDatabase): BookDao = database.bookDao()

    @Provides
    fun provideAccountDao(database: XRecordDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideCategoryDao(database: XRecordDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: XRecordDatabase): TransactionDao = database.transactionDao()
}
