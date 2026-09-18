package top.tobin.xrecord.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.core.security.PasswordHasher
import top.tobin.xrecord.data.local.XRecordDatabase
import top.tobin.xrecord.data.local.dao.UserDao
import top.tobin.xrecord.data.local.dao.LocalAccount
import top.tobin.xrecord.data.local.dao.AccountDataSummary
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

    /** 本机已注册的账号列表，供切换账号使用。 */
    fun observeLocalAccounts(): Flow<List<LocalAccount>> = userDao.observeLocalAccounts()

    /**
     * 切换到本机的另一个账号。
     *
     * 必须验证目标账号的密码：本地多账号的意义就在于彼此不可见，
     * 如果切换不需要密码，隔离就形同虚设。
     */
    suspend fun switchAccount(userId: Long, password: String): AuthResult =
        withContext(ioDispatcher) {
            val user = userDao.findById(userId)
                ?: return@withContext AuthResult.Failure(AuthError.CREDENTIALS_INVALID)

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
        val user =
            userDao.findByUsername(normalizedUsername) ?:
            // 用户名不存在与密码错误返回同一个结果，避免暴露哪些用户名已被占用
            return@withContext AuthResult.Failure(
                AuthError.CREDENTIALS_INVALID
            )

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

    suspend fun dataSummary(userId: Long): AccountDataSummary =
        withContext(ioDispatcher) { userDao.dataSummary(userId) }

    /**
     * 删除账号及其全部数据，并退出登录。
     *
     * 只允许删除当前登录的账号：删除其它账号意味着在不知道对方密码的情况下销毁其数据，
     * 这与"本地多账号彼此隔离"的设计直接冲突。此处做一次服务端（数据层）校验，
     * 即便将来界面上出现疏漏也不会误删他人数据。
     *
     * @return 是否真正执行了删除
     */
    suspend fun deleteCurrentAccount(userId: Long): Boolean = withContext(ioDispatcher) {
        if (settings.currentUserId.first() != userId) {
            return@withContext false
        }

        database.withTransaction { userDao.deleteById(userId) }

        // 数据库事务提交后再清登录态：即便这一步失败，会话流发现用户已不存在
        // 也会回落到未登录态，不会停在空白主界面
        settings.setCurrentUserId(null)
        true
    }

    /** 统一小写并去空格，让登录名大小写不敏感，显示名交给昵称。 */
    private fun normalizeUsername(username: String): String = username.trim().lowercase()

    companion object {
        const val MIN_PASSWORD_LENGTH = 6

        /** 3~20 位字母、数字、下划线或中文。 */
        val USERNAME_PATTERN = Regex("^[a-z0-9_\\u4e00-\\u9fa5]{3,20}$")
    }
}
