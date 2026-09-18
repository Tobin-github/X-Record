package top.tobin.xrecord.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.core.security.PasswordHasher
import top.tobin.xrecord.data.local.XRecordDatabase
import top.tobin.xrecord.data.local.dao.UserDao
import top.tobin.xrecord.data.local.entity.UserEntity
import top.tobin.xrecord.data.preferences.SettingsDataSource

/** 登录态。 */
sealed interface SessionState {
    data object Loading : SessionState

    data object LoggedOut : SessionState

    data class LoggedIn(val user: UserEntity) : SessionState
}

/** 注册与登录的具体失败原因，由 UI 映射成文案。 */
enum class AuthError {
    USERNAME_INVALID,
    USERNAME_TAKEN,
    PASSWORD_TOO_SHORT,
    PASSWORD_MISMATCH,
    CREDENTIALS_INVALID,
    UNKNOWN,
}

sealed interface AuthResult {
    data object Success : AuthResult

    data class Failure(val error: AuthError) : AuthResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val database: XRecordDatabase,
    private val userDao: UserDao,
    private val settings: SettingsDataSource,
    private val defaultDataInitializer: DefaultDataInitializer,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * 当前登录用户。
     *
     * 以 DataStore 里的 id 为准去查 Room，而不是把用户对象缓存到内存：用户在"我的"页
     * 改了昵称或头像后，这里会自动跟着更新。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionState: Flow<SessionState> = settings.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(SessionState.LoggedOut)
            } else {
                userDao.observeById(userId).map { user ->
                    // 用户行不存在（例如数据被清空）时视为未登录，避免停留在空白主界面
                    if (user == null) SessionState.LoggedOut else SessionState.LoggedIn(user)
                }
            }
        }
        .onStart { emit(SessionState.Loading) }
        .distinctUntilChanged()

    /** 当前登录用户 id，未登录为 null。业务仓库只依赖这个值，不关心用户对象本身。 */
    val currentUserId: Flow<Long?> = settings.currentUserId

    suspend fun register(
        username: String,
        nickname: String,
        password: String,
        confirmPassword: String,
    ): AuthResult = withContext(ioDispatcher) {
        val normalizedUsername = normalizeUsername(username)
        when {
            !USERNAME_PATTERN.matches(normalizedUsername) ->
                return@withContext AuthResult.Failure(AuthError.USERNAME_INVALID)

            password.length < MIN_PASSWORD_LENGTH ->
                return@withContext AuthResult.Failure(AuthError.PASSWORD_TOO_SHORT)

            password != confirmPassword ->
                return@withContext AuthResult.Failure(AuthError.PASSWORD_MISMATCH)
        }

        if (userDao.findByUsername(normalizedUsername) != null) {
            return@withContext AuthResult.Failure(AuthError.USERNAME_TAKEN)
        }

        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(
            password = password.toCharArray(),
            salt = salt,
            iterations = PasswordHasher.DEFAULT_ITERATIONS,
        )
        val now = System.currentTimeMillis()

        val userId = try {
            database.withTransaction {
                val newUserId = userDao.insert(
                    UserEntity(
                        username = normalizedUsername,
                        nickname = nickname.trim().ifEmpty { normalizedUsername },
                        passwordHash = hash,
                        passwordSalt = salt,
                        passwordIterations = PasswordHasher.DEFAULT_ITERATIONS,
                        createdAt = now,
                        lastLoginAt = now,
                    ),
                )
                defaultDataInitializer.createDefaults(newUserId, now)
                newUserId
            }
        } catch (exception: android.database.sqlite.SQLiteConstraintException) {
            // 唯一索引兜底：上面的查询与插入之间存在并发窗口
            return@withContext AuthResult.Failure(AuthError.USERNAME_TAKEN)
        }

        settings.setCurrentUserId(userId)
        AuthResult.Success
    }

    suspend fun login(username: String, password: String): AuthResult = withContext(ioDispatcher) {
        val normalizedUsername = normalizeUsername(username)
        val user = userDao.findByUsername(normalizedUsername)

        // 用户名不存在与密码错误返回同一个结果，避免暴露哪些用户名已被占用
        if (user == null) {
            return@withContext AuthResult.Failure(AuthError.CREDENTIALS_INVALID)
        }

        val matched = PasswordHasher.verify(
            password = password.toCharArray(),
            salt = user.passwordSalt,
            iterations = user.passwordIterations,
            expectedHash = user.passwordHash,
        )
        if (!matched) {
            return@withContext AuthResult.Failure(AuthError.CREDENTIALS_INVALID)
        }

        userDao.touchLastLogin(user.id, System.currentTimeMillis())
        settings.setCurrentUserId(user.id)
        AuthResult.Success
    }

    suspend fun logout() {
        settings.setCurrentUserId(null)
    }

    /** 统一小写并去空格，让登录名大小写不敏感，显示名交给昵称。 */
    private fun normalizeUsername(username: String): String = username.trim().lowercase()

    companion object {
        const val MIN_PASSWORD_LENGTH = 6

        /** 3~20 位字母、数字、下划线或中文。 */
        val USERNAME_PATTERN = Regex("^[a-z0-9_\\u4e00-\\u9fa5]{3,20}$")
    }
}
