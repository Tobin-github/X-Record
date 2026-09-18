package top.tobin.xrecord.core.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.data.preferences.SettingsDataSource

sealed interface AppLockState {
    /** 还没读到配置。此时一律不渲染主界面，避免启用锁的情况下内容先闪一下。 */
    data object Unknown : AppLockState

    data object Locked : AppLockState

    data object Unlocked : AppLockState
}

/**
 * 应用锁。
 *
 * 与登录密码是两回事：登录密码区分的是"本机上的哪个账号"，
 * 应用锁保护的是"这台手机上的这个应用"，因此配置存在设备级的 DataStore 里，
 * 与当前登录的账号无关。
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsDataSource,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** null 表示本次会话还没有明确结论，此时以配置为准（启用即视为锁定）。 */
    private val sessionUnlocked = MutableStateFlow<Boolean?>(null)

    private var backgroundedAt: Long? = null

    val state: StateFlow<AppLockState> = combine(
        settings.appLockConfig,
        sessionUnlocked,
    ) { config, unlocked ->
        when {
            !config.enabled || !config.hasPin -> AppLockState.Unlocked
            unlocked == true -> AppLockState.Unlocked
            // 启用锁但本次会话尚未解锁 —— 冷启动时先锁上，等用户输入密码
            else -> AppLockState.Locked
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppLockState.Unknown,
    )

    suspend fun verify(pin: String): Boolean {
        val config = settings.appLockConfig.first()
        val hash = config.pinHash ?: return false
        val salt = config.pinSalt ?: return false
        return PasswordHasher.verify(pin.toCharArray(), salt, config.pinIterations, hash)
    }

    /** 设置密码并启用应用锁。当前会话保持解锁，不会刚设完就把用户挡在外面。 */
    suspend fun enable(pin: String) {
        // 必须先把本次会话标记为已解锁，再写入配置。
        // 否则在"启用"落库、而 sessionUnlocked 仍为 null 的那一瞬间，
        // 状态机会判定为锁定，导致根导航切换分支、整个主界面树连同导航栈被重建，
        // 用户刚设完密码就被弹回首页。
        sessionUnlocked.value = true

        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(
            password = pin.toCharArray(),
            salt = salt,
            iterations = PasswordHasher.DEFAULT_ITERATIONS,
        )
        settings.setAppLockPin(hash, salt, PasswordHasher.DEFAULT_ITERATIONS)
        settings.setAppLockEnabled(true)
    }

    suspend fun changePin(pin: String) {
        sessionUnlocked.value = true
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(pin.toCharArray(), salt, PasswordHasher.DEFAULT_ITERATIONS)
        settings.setAppLockPin(hash, salt, PasswordHasher.DEFAULT_ITERATIONS)
    }

    suspend fun disable() {
        // 先关生物识别再关锁：否则会留下"锁已关闭但生物识别仍开着"的配置
        settings.setAppLockBiometric(false)
        settings.setAppLockEnabled(false)
        sessionUnlocked.value = true
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        settings.setAppLockBiometric(enabled)
    }

    suspend fun setTimeoutSeconds(seconds: Int) {
        settings.setAppLockTimeoutSeconds(seconds)
    }

    fun unlock() {
        sessionUnlocked.value = true
    }

    /** 手动立即上锁（例如从设置里点"立即锁定"）。 */
    fun lock() {
        sessionUnlocked.value = false
    }

    fun onBackgrounded(now: Long) {
        backgroundedAt = now
    }

    /**
     * 回到前台时判断是否需要重新解锁。
     *
     * 冷启动不走这里：那时 [sessionUnlocked] 仍为 null，状态本身就会是锁定。
     */
    fun onForegrounded(now: Long) {
        val leftAt = backgroundedAt ?: return
        backgroundedAt = null

        scope.launch {
            val config = settings.appLockConfig.first()
            if (!config.enabled || !config.hasPin) return@launch
            if (config.timeoutSeconds < 0) return@launch

            val elapsedSeconds = (now - leftAt) / 1000
            if (elapsedSeconds >= config.timeoutSeconds) {
                sessionUnlocked.value = false
            }
        }
    }
}
