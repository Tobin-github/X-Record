package top.tobin.xrecord.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 本地账号的密码派生。
 *
 * 登录完全离线，但密码仍然不能明文落库：手机的备份、root、调试拉取都可能把数据库文件
 * 带走。这里使用 PBKDF2WithHmacSHA256 + 每用户独立随机盐，迭代次数随哈希一起存库，
 * 这样后续提高强度时老密码依然可以校验（用记录里的旧迭代次数验证，成功后按新强度重算）。
 */
object PasswordHasher {

    /**
     * 迭代次数。每提高一倍，暴力破解成本翻倍，登录耗时也相应增加。
     * 10 万次在主流机型上约数百毫秒，是可用性和强度的折中。
     */
    const val DEFAULT_ITERATIONS = 100_000

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    fun newSalt(): String {
        val bytes = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(
        password: CharArray,
        salt: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String = derive(password, Base64.getDecoder().decode(salt), iterations)

    /**
     * 校验密码。
     *
     * 使用 [MessageDigest.isEqual] 做定长时间比较，避免通过响应时间差异逐字节猜测哈希。
     */
    fun verify(
        password: CharArray,
        salt: String,
        iterations: Int,
        expectedHash: String,
    ): Boolean {
        val actualHash = hash(password, salt, iterations)
        return MessageDigest.isEqual(
            actualHash.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8),
        )
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec)
            Base64.getEncoder().encodeToString(key.encoded)
        } finally {
            spec.clearPassword()
        }
    }
}
