package com.windroid.nexus.data.vfs

import android.content.Context
import com.windroid.nexus.core.util.DispatcherProvider
import com.windroid.nexus.domain.model.VfsNode
import com.windroid.nexus.domain.repository.VfsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Virtual File System manager.
 *
 * Presents the app-private rootfs as a Windows drive tree. `C:\` maps to
 * `<filesDir>/rootfs/drive_c`; additional drive letters can be mounted onto
 * arbitrary host trees (e.g. a user-picked SAF folder exposed as `D:\`).
 *
 * All path translation is centralised here and defends against traversal: a
 * resolved host path that escapes its mount root is rejected.
 */
@Singleton
class VfsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : VfsRepository {

    private val rootfs = File(context.filesDir, "rootfs")

    /** driveLetter(upper) -> host root directory for that drive. */
    private val mounts = linkedMapOf(
        'C' to File(rootfs, "drive_c"),
    )

    override suspend fun list(windowsPath: String): List<VfsNode> =
        withContext(dispatchers.io) {
            val dir = toHostFile(windowsPath)
            if (!dir.isDirectory) return@withContext emptyList()
            dir.listFiles()
                ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                ?.map { it.toVfsNode(windowsPath) }
                ?: emptyList()
        }

    override suspend fun createDirectory(windowsPath: String): VfsNode =
        withContext(dispatchers.io) {
            val target = toHostFile(windowsPath)
            if (!target.exists() && !target.mkdirs()) {
                throw IOException("Could not create directory: $windowsPath")
            }
            target.toVfsNode(parentWindowsPath(windowsPath))
        }

    override suspend fun delete(windowsPath: String): Boolean =
        withContext(dispatchers.io) {
            toHostFile(windowsPath).deleteRecursivelySafely()
        }

    override suspend fun readText(windowsPath: String): String =
        withContext(dispatchers.io) {
            val file = toHostFile(windowsPath)
            if (!file.isFile) throw IOException("Not a file: $windowsPath")
            file.readText()
        }

    override suspend fun writeText(windowsPath: String, content: String): VfsNode =
        withContext(dispatchers.io) {
            val file = toHostFile(windowsPath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            file.toVfsNode(parentWindowsPath(windowsPath))
        }

    override suspend fun mountExternal(driveLetter: Char, hostTreePath: String) =
        withContext(dispatchers.io) {
            val root = File(hostTreePath)
            require(root.isDirectory) { "Mount source is not a directory: $hostTreePath" }
            mounts[driveLetter.uppercaseChar()] = root
        }

    // ---------------------------------------------------------------------
    // Path translation (the heart of the VFS)
    // ---------------------------------------------------------------------

    /**
     * Converts `C:\Users\nexus\file.txt` to the backing [File], validating that
     * the result stays inside the mount root (no `..` escape).
     */
    private fun toHostFile(windowsPath: String): File {
        val normalised = windowsPath.trim().ifBlank { "C:\\" }
        val match = DRIVE_REGEX.find(normalised)
            ?: throw IOException("Malformed Windows path: $windowsPath")
        val (letterRaw, remainderRaw) = match.destructured
        val letter = letterRaw.first().uppercaseChar()
        val mountRoot = mounts[letter]
            ?: throw IOException("Drive $letter: is not mounted")

        val relative = remainderRaw.trim('\\', '/').replace('\\', '/')
        val resolved = File(mountRoot, relative).canonicalFile
        val rootCanonical = mountRoot.canonicalFile
        if (!resolved.path.startsWith(rootCanonical.path)) {
            throw IOException("Path traversal blocked: $windowsPath")
        }
        return resolved
    }

    private fun File.toVfsNode(parentWindowsPath: String): VfsNode {
        val driveLetter = DRIVE_REGEX.find(parentWindowsPath)?.groupValues?.get(1)?.uppercase() ?: "C"
        val mountRoot = mounts[driveLetter[0]]!!.canonicalFile
        val rel = canonicalFile.path.removePrefix(mountRoot.path).trimStart('/')
        val winPath = buildString {
            append(driveLetter).append(":\\")
            append(rel.replace('/', '\\'))
        }
        return VfsNode(
            windowsPath = winPath,
            hostPath = absolutePath,
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isFile) length() else 0L,
            lastModifiedMillis = lastModified(),
        )
    }

    private fun parentWindowsPath(windowsPath: String): String =
        windowsPath.substringBeforeLast('\\', "C:").ifBlank { "C:\\" }

    private fun File.deleteRecursivelySafely(): Boolean =
        runCatching { deleteRecursively() }.getOrDefault(false)

    companion object {
        private val DRIVE_REGEX = Regex("^([A-Za-z]):\\\\?(.*)$")
    }
}
