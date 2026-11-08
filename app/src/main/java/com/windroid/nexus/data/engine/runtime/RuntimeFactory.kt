package com.windroid.nexus.data.engine.runtime

import com.windroid.nexus.domain.engine.NativeRuntime
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the ordered list of backend strategies for a given rootfs. Ordering
 * encodes preference: lightest-first (PRoot), then Box64+Wine, then full QEMU.
 * [EngineBridge] walks this list and selects the first provisioned backend.
 *
 * Factored out so tests can inject a factory that returns fakes.
 */
@Singleton
class RuntimeFactory @Inject constructor() {

    fun discover(rootfs: File): List<NativeRuntime> = listOf(
        ProotRuntime(rootfs),
        Box64WineRuntime(rootfs),
        QemuSystemRuntime(rootfs),
    )
}
