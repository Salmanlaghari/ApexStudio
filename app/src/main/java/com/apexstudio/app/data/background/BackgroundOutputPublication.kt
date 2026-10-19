package com.apexstudio.app.data.background

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** Publish a completed sibling without replacing a file that editor history may reference.
 * File.renameTo may overwrite on Android/Linux; Files.move without REPLACE_EXISTING does not.
 * Engine calls are serialized on its single worker. Only the caller-owned temporary is removed.
 */
internal fun publishBackgroundOutput(temporary: File, output: File): File {
    require(temporary.isFile && temporary.length() > 0L) { "Encoding produced an empty file." }
    try {
        Files.move(temporary.toPath(), output.toPath())
    } catch (exists: FileAlreadyExistsException) {
        if (!output.isFile || output.length() <= 0L) throw exists
        // Another complete cache entry wins; it must never be overwritten.
        Files.deleteIfExists(temporary.toPath())
    }
    return output
}
