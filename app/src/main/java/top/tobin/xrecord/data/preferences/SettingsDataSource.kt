package top.tobin.xrecord.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.tobin.xrecord.core.util.PeriodCalculator
import top.tobin.xrecord.core.security.PasswordHasher

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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        val APP_LOCK_PIN_SALT = stringPreferencesKey("app_lock_pin_salt")
        val APP_LOCK_PIN_ITERATIONS = intPreferencesKey("app_lock_pin_iterations")
        val APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric")
        val APP_LOCK_TIMEOUT_SECONDS = intPreferencesKey("app_lock_timeout_seconds")
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

    /** 主题模式：跟随系统 / 浅色 / 深色。 */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .map { preferences ->
            preferences[Keys.THEME_MODE]
                ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: ThemeMode.FOLLOW_SYSTEM
        }

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[Keys.DYNAMIC_COLOR] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    /**
     * 应用锁配置。
     *
     * 这些值是**设备级**的，与登录账号无关：锁保护的是"这台手机上的这个应用"，
     * 而不是某一个账号。
     */
    val appLockConfig: Flow<AppLockConfig> = context.settingsDataStore.data
        .map { preferences ->
            AppLockConfig(
                enabled = preferences[Keys.APP_LOCK_ENABLED] ?: false,
                pinHash = preferences[Keys.APP_LOCK_PIN_HASH],
                pinSalt = preferences[Keys.APP_LOCK_PIN_SALT],
                pinIterations = preferences[Keys.APP_LOCK_PIN_ITERATIONS]
                    ?: PasswordHasher.DEFAULT_ITERATIONS,
                biometricEnabled = preferences[Keys.APP_LOCK_BIOMETRIC] ?: false,
                timeoutSeconds = preferences[Keys.APP_LOCK_TIMEOUT_SECONDS]
                    ?: DEFAULT_LOCK_TIMEOUT_SECONDS,
            )
        }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAppLockPin(hash: String, salt: String, iterations: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.APP_LOCK_PIN_HASH] = hash
            preferences[Keys.APP_LOCK_PIN_SALT] = salt
            preferences[Keys.APP_LOCK_PIN_ITERATIONS] = iterations
        }
    }

    suspend fun setAppLockBiometric(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.APP_LOCK_BIOMETRIC] = enabled }
    }

    suspend fun setAppLockTimeoutSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.APP_LOCK_TIMEOUT_SECONDS] = seconds }
    }

    companion object {
        /** 默认退到后台 30 秒后需要重新解锁。 */
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 30
    }
}

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

/** 应用锁配置。[pinHash] 为空表示还没设置过密码。 */
data class AppLockConfig(
    val enabled: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinIterations: Int = PasswordHasher.DEFAULT_ITERATIONS,
    val biometricEnabled: Boolean = false,
    val timeoutSeconds: Int = SettingsDataSource.DEFAULT_LOCK_TIMEOUT_SECONDS,
) {
    val hasPin: Boolean get() = !pinHash.isNullOrEmpty() && !pinSalt.isNullOrEmpty()
}
