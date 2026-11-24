package com.hydrogen.ai.data

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Writes replacement JSON in the same directory without deleting the previous good copy first.
 *
 * Use an atomic move when supported. Otherwise replace the destination in one move operation.
 * Neither path deliberately deletes the last known-good target before replacement.
 * The non-atomic fallback cannot guarantee recovery from every filesystem or power failure.
 */
internal object SafeFileReplace {
    fun writeText(
        target: File,
        text: String,
        replace: (Path, Path) -> Unit = ::replaceFile
    ) {
        val destination = target.absoluteFile
        val parent = destination.parentFile
            ?: throw IOException("The destination has no parent directory")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Cannot create directory for saved data")
        }

        val temporary = Files.createTempFile(parent.toPath(), destination.name + ".", ".tmp").toFile()
        try {
            temporary.writeText(text, Charsets.UTF_8)
            replace(temporary.toPath(), destination.toPath())
        } finally {
            // Never remove the previously saved destination on a failed replacement.
            // Do not leave a second copy of sensitive data behind in the temp file.
            Files.deleteIfExists(temporary.toPath())
        }
    }

    /** Remove a saved snapshot and abandoned temp files on an explicit user deletion.
     * Callers serialize deletes with writes to the same store.
     */
    fun deleteSnapshot(target: File) {
        val destination = target.absoluteFile
        val parent = destination.parentFile ?: throw IOException("No parent directory")
        val abandoned = parent.listFiles { entry ->
            entry.name == destination.name + ".tmp" ||
                (entry.name.startsWith(destination.name + ".") && entry.name.endsWith(".tmp"))
        }
        abandoned?.forEach { Files.deleteIfExists(it.toPath()) }
        Files.deleteIfExists(destination.toPath())
    }

    private fun replaceFile(source: Path, destination: Path) {
        try {
            Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, REPLACE_EXISTING)
        } catch (_: FileAlreadyExistsException) {
            Files.move(source, destination, REPLACE_EXISTING)
        }
    }
}
