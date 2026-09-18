package top.tobin.xrecord.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.tobin.xrecord.core.util.PeriodCalculator

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "x_record_settings",
)

/**
 * 应用设置与登录态。
 *
 * 只存"一台设备一份"的轻量配置。业务数据（哪怕只有一条）一律进 Room，
 * 避免出现两套存储各自演进、数据对不上的情况。
 */
@Singleton
class SettingsDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val CURRENT_USER_ID = longPreferencesKey("current_user_id")
        val CURRENT_BOOK_ID = longPreferencesKey("current_book_id")
        val PERIOD_START_DAY = intPreferencesKey("period_start_day")
    }

    /** 当前登录用户 id，null 表示未登录。 */
    val currentUserId: Flow<Long?> = context.settingsDataStore.data
        .map { preferences -> preferences[Keys.CURRENT_USER_ID] }

    /** 当前选中的账本。为空时由仓库回落到默认账本。 */
    val currentBookId: Flow<Long?> = context.settingsDataStore.data
        .map { preferences -> preferences[Keys.CURRENT_BOOK_ID] }

    /** 每月起始日，默认 1 号。 */
    val periodStartDay: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            PeriodCalculator.normalizeStartDay(
                preferences[Keys.PERIOD_START_DAY] ?: PeriodCalculator.MIN_START_DAY,
            )
        }

    suspend fun setCurrentUserId(userId: Long?) {
        context.settingsDataStore.edit { preferences ->
            if (userId == null) {
                preferences.remove(Keys.CURRENT_USER_ID)
            } else {
                preferences[Keys.CURRENT_USER_ID] = userId
            }
        }
    }

    suspend fun setPeriodStartDay(day: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.PERIOD_START_DAY] = PeriodCalculator.normalizeStartDay(day)
        }
    }

    suspend fun setCurrentBookId(bookId: Long?) {
        context.settingsDataStore.edit { preferences ->
            if (bookId == null) {
                preferences.remove(Keys.CURRENT_BOOK_ID)
            } else {
                preferences[Keys.CURRENT_BOOK_ID] = bookId
            }
        }
    }
}
