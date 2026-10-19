package com.apexstudio.app.data.media

/**
 * Runs [operation] with an owned resource and always attempts [release], including when the
 * operation throws. Cleanup failures are ignored so they do not mask the original result/error.
 */
internal inline fun <T : Any, R> withResourceRelease(
    resource: T,
    release: (T) -> Unit,
    operation: (T) -> R,
): R {
    try {
        return operation(resource)
    } finally {
        try {
            release(resource)
        } catch (_: Exception) {
            // Native release methods can fail after partial teardown; preserve operation outcome.
        }
    }
}
