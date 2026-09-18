package top.tobin.xrecord.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    // 单元测试用低迭代次数，避免拖慢构建；算法本身与生产一致
    private val iterations = 1_000

    @Test
    fun `相同密码与盐得到相同哈希`() {
        val salt = PasswordHasher.newSalt()

        val first = PasswordHasher.hash("hunter2".toCharArray(), salt, iterations)
        val second = PasswordHasher.hash("hunter2".toCharArray(), salt, iterations)

        assertEquals(first, second)
    }

    @Test
    fun `相同密码配不同盐会得到不同哈希`() {
        val first = PasswordHasher.hash("hunter2".toCharArray(), PasswordHasher.newSalt(), iterations)
        val second = PasswordHasher.hash("hunter2".toCharArray(), PasswordHasher.newSalt(), iterations)

        assertNotEquals(first, second)
    }

    @Test
    fun `每次生成的盐都不相同`() {
        val salts = List(50) { PasswordHasher.newSalt() }

        assertEquals(50, salts.toSet().size)
    }

    @Test
    fun `哈希结果不含明文密码`() {
        val hash = PasswordHasher.hash("hunter2".toCharArray(), PasswordHasher.newSalt(), iterations)

        assertFalse(hash.contains("hunter2"))
        assertNotEquals("hunter2", hash)
    }

    @Test
    fun `正确密码校验通过`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("correct horse".toCharArray(), salt, iterations)

        assertTrue(PasswordHasher.verify("correct horse".toCharArray(), salt, iterations, hash))
    }

    @Test
    fun `错误密码校验失败`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("correct horse".toCharArray(), salt, iterations)

        assertFalse(PasswordHasher.verify("wrong horse".toCharArray(), salt, iterations, hash))
        assertFalse(PasswordHasher.verify("".toCharArray(), salt, iterations, hash))
        assertFalse(PasswordHasher.verify("Correct horse".toCharArray(), salt, iterations, hash))
    }

    @Test
    fun `迭代次数不同则校验失败`() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("hunter2".toCharArray(), salt, iterations)

        // 模拟用户记录里的迭代次数与实际不符的情况，必须拒绝而不是放行
        assertFalse(PasswordHasher.verify("hunter2".toCharArray(), salt, iterations * 2, hash))
    }

    @Test
    fun `中文与表情符号密码同样可用`() {
        val salt = PasswordHasher.newSalt()
        val password = "密码🔒很安全".toCharArray()
        val hash = PasswordHasher.hash(password, salt, iterations)

        assertTrue(PasswordHasher.verify("密码🔒很安全".toCharArray(), salt, iterations, hash))
    }
}
