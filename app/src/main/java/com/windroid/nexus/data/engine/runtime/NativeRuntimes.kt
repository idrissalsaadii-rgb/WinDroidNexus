package com.windroid.nexus.data.engine.runtime

import com.windroid.nexus.domain.engine.NativeRuntime
import com.windroid.nexus.domain.model.RuntimeKind
import com.windroid.nexus.domain.model.RuntimeProfile
import java.io.File

/**
 * Concrete [NativeRuntime] strategies.
 *
 * IMPORTANT (honest engineering note): none of these ship the actual native
 * binaries. PRoot, Box64, Wine and QEMU are large GPL/LGPL payloads that the
 * user provisions into the rootfs (Settings → Engine). Each strategy here
 * knows *how to drive* its backend once present, and reports isProvisioned()
 * == false until the launcher binary is on disk and executable. The
 * EngineBridge uses that signal to pick a usable backend or surface a clear
 * "no runtime" error instead of crashing.
 */

/** Shared path math for a rootfs laid out like a tiny Linux distro. */
internal abstract class BaseRuntime(
    protected val rootfs: File,
) : NativeRuntime {

    protected fun bin(name: String) = File(rootfs, "usr/bin/$name")

    /** Turns `C:\Users\nexus\app.exe` into `/drive_c/Users/nexus/app.exe`. */
    override fun resolveGuestPath(windowsOrHostPath: String): String {
        val p = windowsOrHostPath.trim()
        // Already a POSIX path — pass through.
        if (p.startsWith("/")) return p
        val driveMatch = Regex("^([A-Za-z]):\\\\?(.*)$").find(p)
            ?: return p.replace('\\', '/')
        val (letter, rest) = driveMatch.destructured
        val normalised = rest.replace('\\', '/')
        return "/drive_${letter.lowercase()}/$normalised"
    }

    protected fun isExecutable(file: File) = file.exists() && file.canExecute()
}

/**
 * PRoot: a user-space implementation of chroot/mount that needs no root.
 * Ideal for ARM-native Linux layers and for hosting Wine's ARM build.
 */
internal class ProotRuntime(rootfs: File) : BaseRuntime(rootfs) {

    private val launcher = bin("proot")

    override val profile = RuntimeProfile(
        kind = RuntimeKind.PROOT,
        displayName = "PRoot (user-space chroot)",
        rootfsPath = rootfs.absolutePath,
        launcherBinary = launcher.absolutePath,
        isAvailable = isExecutable(launcher),
        version = readVersion(launcher),
    )

    override fun isProvisioned() = isExecutable(launcher)

    override fun buildCommand(guestExecutable: String, guestArgs: List<String>): List<String> {
        val guest = resolveGuestPath(guestExecutable)
        return buildList {
            add(launcher.absolutePath)
            add("-r"); add(rootfs.absolutePath)      // new root
            add("-0")                                  // fake root uid
            add("-w"); add("/root")                   // working dir
            // Bind the kernel interfaces the guest libc expects.
            listOf("/dev", "/proc", "/sys").forEach { add("-b"); add(it) }
            add("/usr/bin/env")
            add("HOME=/root")
            add("PATH=/usr/bin:/bin")
            add(guest)
            addAll(guestArgs)
        }
    }

    override fun environment() = mapOf(
        "PROOT_NO_SECCOMP" to "1",   // broadens device compatibility
        "PROOT_TMP_DIR" to File(rootfs, "tmp").absolutePath,
    )

    private fun readVersion(f: File): String? =
        if (isExecutable(f)) "proot (provisioned)" else null
}

/**
 * Box64 + Wine: translates x86_64 instructions to ARM at the syscall/library
 * boundary and runs the Windows PE loader (Wine). This is the common path for
 * running real .exe files on ARM phones.
 */
internal class Box64WineRuntime(rootfs: File) : BaseRuntime(rootfs) {

    private val box64 = bin("box64")
    private val wine = File(rootfs, "opt/wine/bin/wine64")
    private val winePrefix = File(rootfs, "drive_c/.wine")

    override val profile = RuntimeProfile(
        kind = RuntimeKind.BOX64_WINE,
        displayName = "Box64 + Wine (x86_64 translation)",
        rootfsPath = rootfs.absolutePath,
        launcherBinary = box64.absolutePath,
        isAvailable = isExecutable(box64) && wine.exists(),
        version = if (isExecutable(box64)) "box64 + wine (provisioned)" else null,
    )

    override fun isProvisioned() = isExecutable(box64) && wine.exists()

    override fun buildCommand(guestExecutable: String, guestArgs: List<String>): List<String> {
        val guest = resolveGuestPath(guestExecutable)
        return buildList {
            add(box64.absolutePath)
            add(wine.absolutePath)
            add(guest)
            addAll(guestArgs)
        }
    }

    override fun environment() = mapOf(
        "WINEPREFIX" to winePrefix.absolutePath,
        "WINEDEBUG" to "-all",               // quiet, faster
        "BOX64_PATH" to File(rootfs, "opt/wine/bin").absolutePath,
        "BOX64_LD_LIBRARY_PATH" to File(rootfs, "opt/wine/lib").absolutePath,
        "DISPLAY" to ":0",                   // served by the bundled X server
    )
}

/**
 * Full-system QEMU: boots an actual guest disk image. Heaviest option, but the
 * only one that runs an unmodified Windows kernel. Driven headless with its
 * display piped to the in-app framebuffer.
 */
internal class QemuSystemRuntime(rootfs: File) : BaseRuntime(rootfs) {

    private val qemu = bin("qemu-system-x86_64")
    private val diskImage = File(rootfs, "images/guest.qcow2")

    override val profile = RuntimeProfile(
        kind = RuntimeKind.QEMU_SYSTEM,
        displayName = "QEMU (full-system x86_64)",
        rootfsPath = rootfs.absolutePath,
        launcherBinary = qemu.absolutePath,
        isAvailable = isExecutable(qemu) && diskImage.exists(),
        version = if (isExecutable(qemu)) "qemu-system (provisioned)" else null,
    )

    override fun isProvisioned() = isExecutable(qemu) && diskImage.exists()

    override fun buildCommand(guestExecutable: String, guestArgs: List<String>): List<String> {
        // In full-system mode the guest executable path is forwarded to the
        // guest agent over a virtio serial channel rather than exec'd directly.
        val payload = resolveGuestPath(guestExecutable)
        return buildList {
            add(qemu.absolutePath)
            add("-machine"); add("q35,accel=tcg")
            add("-m"); add("2048")
            add("-smp"); add("2")
            add("-drive"); add("file=${diskImage.absolutePath},if=virtio,format=qcow2")
            add("-nographic")
            add("-serial"); add("mon:stdio")
            // Pass the requested payload to the in-guest launcher agent.
            add("-append"); add("nexus.run=$payload ${guestArgs.joinToString(" ")}")
        }
    }

    override fun environment() = mapOf(
        "QEMU_AUDIO_DRV" to "none",
    )
}
