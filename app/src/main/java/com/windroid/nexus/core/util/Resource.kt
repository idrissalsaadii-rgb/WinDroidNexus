package com.windroid.nexus.core.util

/**
 * A minimal result wrapper used across layer boundaries so callers can
 * distinguish success, in-flight, and failure without throwing across threads.
 */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>
}

/** Runs [block], wrapping any throwable into [Resource.Error]. */
inline fun <T> resourceCatching(block: () -> T): Resource<T> = try {
    Resource.Success(block())
} catch (t: Throwable) {
    Resource.Error(t.message ?: t.javaClass.simpleName, t)
}
