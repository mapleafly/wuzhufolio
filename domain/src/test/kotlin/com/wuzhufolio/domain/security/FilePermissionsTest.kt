package com.wuzhufolio.domain.security

import com.wuzhufolio.domain.security.FilePermissions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M13 T13.1 安全自查加固：敏感文件权限（AGENTS.md §1.1 硬约束 3）。
 * 非 POSIX 平台（Windows）无权限模型，权限断言按平台能力跳过（与 MasterKeyStoreTest 同口径）。
 */
class FilePermissionsTest {

    private val posix: Boolean =
        java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private fun perms(path: Path): Set<PosixFilePermission> = Files.getPosixFilePermissions(path)

    @Test
    fun `owner only write creates the file with 0600 from the start`() {
        val dir = Files.createTempDirectory("wuzhufolio-perm-test")
        val file = dir.resolve("secret.txt")
        FilePermissions.writeOwnerOnlyString(file, "sensitive")
        assertEquals("sensitive", Files.readString(file))
        if (posix) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                perms(file),
                "创建即 0600（不得出现先写后 chmod 的 umask 窗口）",
            )
        }
    }

    @Test
    fun `owner only write tightens an existing file`() {
        val dir = Files.createTempDirectory("wuzhufolio-perm-test")
        val file = dir.resolve("existing.txt")
        Files.writeString(file, "old")
        if (posix) {
            Files.setPosixFilePermissions(
                file,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ,
                ),
            )
        }
        FilePermissions.writeOwnerOnlyBytes(file, "new".toByteArray())
        assertEquals("new", Files.readString(file))
        if (posix) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                perms(file),
                "已存在文件写后补收紧",
            )
        }
    }

    @Test
    fun `ensure owner only directory creates and tightens to 0700`() {
        val root = Files.createTempDirectory("wuzhufolio-perm-test")
        val dir = root.resolve("data")
        FilePermissions.ensureOwnerOnlyDirectory(dir)
        assertTrue(Files.isDirectory(dir), "目录应被创建")
        // 幂等：再次调用不抛且仍为 0700
        FilePermissions.ensureOwnerOnlyDirectory(dir)
        if (posix) {
            assertEquals(
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
                perms(dir),
                "数据目录 0700（同机其他用户不可遍历）",
            )
        }
    }

    @Test
    fun `restrict is a no-op for missing paths`() {
        val dir = Files.createTempDirectory("wuzhufolio-perm-test")
        // 收紧失败/目标不存在不抛（加固不阻断主流程——WAL/SHM 文件可能尚未创建）
        FilePermissions.restrictFile(dir.resolve("not-there"))
        FilePermissions.restrictDirectory(dir.resolve("not-there"))
    }
}
