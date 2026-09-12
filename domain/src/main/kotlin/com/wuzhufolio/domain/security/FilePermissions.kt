package com.wuzhufolio.domain.security

import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet

/**
 * 敏感文件/目录权限（M13 T13.1 安全自查加固；AGENTS.md §1.1 硬约束 3「密钥与加密」）。
 *
 * 背景（M13 安全自查发现项）：密钥文件此前「先写入、后 chmod」——默认 umask(022) 下存在短暂的 0644 窗口；
 * 数据目录、明文 CSV 导出、日志导出此前完全依赖用户 umask（同机其他用户可读）。
 * 本对象把两条纪律收敛到一处：
 * 1. [ownerOnlyFileAttributes]：**创建即 0600**（消除先写后收紧窗口）；
 * 2. [restrictFile] / [restrictDirectory]：对已存在对象补收紧（0600 / 0700）。
 *
 * 平台口径：非 POSIX 文件系统（Windows NTFS）无 POSIX 权限模型，静默跳过、依赖用户目录 ACL
 * （与 M1 密钥文件既有口径一致）；权限收紧属纵深加固，失败不阻断主流程（数据本身仍有整库加密/字段加密兜底）。
 */
object FilePermissions {

    private const val OWNER_RW = "rw-------"
    private const val OWNER_RWX = "rwx------"

    private val posixSupported: Boolean =
        FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private val ownerFilePermissions: Set<PosixFilePermission> =
        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

    private val ownerDirectoryPermissions: Set<PosixFilePermission> =
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )

    /** 新建文件的 0600 属性（非 POSIX 平台为空数组——`Files.createFile` 不接受 POSIX 属性）。 */
    fun ownerOnlyFileAttributes(): Array<FileAttribute<*>> =
        if (posixSupported) {
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_RW)))
        } else {
            emptyArray()
        }

    /** 新建目录的 0700 属性（非 POSIX 平台为空数组）。 */
    fun ownerOnlyDirectoryAttributes(): Array<FileAttribute<*>> =
        if (posixSupported) {
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_RWX)))
        } else {
            emptyArray()
        }

    /**
     * 以 0600 语义写入文本（密钥类文件）：不存在则**创建即 0600**（无窗口）；已存在则写后补收紧
     * （创建属性只对新建生效）。IO 异常原样上抛，由调用方包装为各自语义的异常。
     */
    @Suppress("SpreadOperator") // 单元素属性数组，无复制开销问题
    fun writeOwnerOnlyString(path: Path, content: String) {
        if (!Files.exists(path)) {
            Files.createFile(path, *ownerOnlyFileAttributes())
        }
        Files.writeString(path, content)
        restrictFile(path)
    }

    /** 以 0600 语义写入字节（导出物：备份/CSV/日志导出等明文或加密载荷）。 */
    @Suppress("SpreadOperator") // 单元素属性数组，无复制开销问题
    fun writeOwnerOnlyBytes(path: Path, bytes: ByteArray) {
        if (!Files.exists(path)) {
            Files.createFile(path, *ownerOnlyFileAttributes())
        }
        Files.write(path, bytes)
        restrictFile(path)
    }

    /** 尽力收紧文件为 0600（非 POSIX 或失败时静默——加固不阻断主流程）。 */
    @Suppress("SwallowedException") // 纵深加固：平台不支持/收紧失败不阻断（数据仍有加密与脱敏兜底）
    fun restrictFile(path: Path) {
        if (!posixSupported) return
        try {
            Files.setPosixFilePermissions(path, ownerFilePermissions)
        } catch (e: UnsupportedOperationException) {
            // 非 POSIX 文件系统（如 Windows NTFS）：依赖用户目录 ACL
        } catch (e: IOException) {
            // 收紧失败不阻断：文件本身仍受整库加密/字段加密/脱敏保护
        }
    }

    /** 尽力收紧目录为 0700（非 POSIX 或失败时静默）。 */
    @Suppress("SwallowedException") // 同 restrictFile 口径
    fun restrictDirectory(path: Path) {
        if (!posixSupported) return
        try {
            Files.setPosixFilePermissions(path, ownerDirectoryPermissions)
        } catch (e: UnsupportedOperationException) {
            // 非 POSIX 文件系统：依赖用户目录 ACL
        } catch (e: IOException) {
            // 收紧失败不阻断
        }
    }

    /** 确保目录存在并收紧为 0700（数据目录/日志目录等应用自有目录使用）。 */
    @Suppress("SpreadOperator") // 单元素属性数组，无复制开销问题
    fun ensureOwnerOnlyDirectory(path: Path): Path {
        if (!Files.isDirectory(path)) {
            Files.createDirectories(path, *ownerOnlyDirectoryAttributes())
        }
        restrictDirectory(path)
        return path
    }
}
