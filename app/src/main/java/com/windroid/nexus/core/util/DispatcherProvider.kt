package com.windroid.nexus.core.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indirection over coroutine dispatchers so blocking engine/IO work can be
 * confined to [io] and swapped for a test dispatcher in unit tests.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main get() = Dispatchers.Main.immediate
    override val io get() = Dispatchers.IO
    override val default get() = Dispatchers.Default
}
