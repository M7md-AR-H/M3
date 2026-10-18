package com.m3.ai.model

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface ModelStatus {
    data object Missing : ModelStatus

    /** A file is present (e.g. copied in over adb) but hasn't been checked yet, or changed since. */
    data object Unverified : ModelStatus

    data object Ready : ModelStatus
}

sealed interface ModelCheckResult {
    data object Ok : ModelCheckResult
    data class WrongSize(val expected: Long, val actual: Long) : ModelCheckResult
    data object WrongHash : ModelCheckResult
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : ModelCheckResult
}

/**
 * Keeps model files in app-private, non-backed-up storage ([directory]) and only marks one
 * [ModelStatus.Ready] after its size and SHA-256 matched the [ModelSpec].
 *
 * Hashing a multi-GB file takes a while, so the result is remembered in a small marker file
 * tied to the file's size and modification time; any change to the file makes it Unverified.
 */
class ModelStore(
    private val directory: File,
    /** Bytes the app may still write in [directory]; the app passes StorageManager's figure. */
    private val freeSpace: (File) -> Long,
) {
    fun fileFor(spec: ModelSpec): File = File(directory, spec.fileName)

    fun status(spec: ModelSpec): ModelStatus {
        val file = fileFor(spec)
        if (!file.isFile) return ModelStatus.Missing
        val marker = readMarker(spec) ?: return ModelStatus.Unverified
        val matches = marker.sha256 == spec.sha256 &&
            marker.sizeBytes == file.length() &&
            marker.lastModified == file.lastModified()
        return if (matches) ModelStatus.Ready else ModelStatus.Unverified
    }

    /**
     * Copies [input] into the store while hashing it. The file only appears under its final
     * name if size and hash match; otherwise the partial copy is deleted. [input] is not closed.
     */
    suspend fun import(
        spec: ModelSpec,
        input: InputStream,
        onProgress: (copiedBytes: Long) -> Unit = {},
    ): ModelCheckResult = withContext(Dispatchers.IO) {
        directory.mkdirs()
        // A leftover partial copy (e.g. the app was killed mid-import) would make free space look too low.
        val partial = File(directory, spec.fileName + PARTIAL_SUFFIX)
        partial.delete()
        // Any existing copy stays until the new one is checked, so the full size is needed.
        val needed = spec.sizeBytes + SPACE_MARGIN_BYTES
        val free = freeSpace(directory)
        if (free < needed) return@withContext ModelCheckResult.NotEnoughSpace(needed, free)

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            partial.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > spec.sizeBytes) {
                        return@withContext ModelCheckResult.WrongSize(spec.sizeBytes, copied)
                    }
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                    onProgress(copied)
                }
                out.fd.sync()
            }
            if (copied != spec.sizeBytes) {
                return@withContext ModelCheckResult.WrongSize(spec.sizeBytes, copied)
            }
            if (digest.digest().toHex() != spec.sha256) return@withContext ModelCheckResult.WrongHash

            val target = fileFor(spec)
            markerFile(spec).delete()
            if (!partial.renameTo(target)) error("Could not move the model into place")
            writeMarker(spec, target)
            ModelCheckResult.Ok
        } finally {
            partial.delete()
        }
    }

    /** Hashes a file that is already in place (e.g. copied in over adb) and marks it Ready. */
    suspend fun verify(
        spec: ModelSpec,
        onProgress: (hashedBytes: Long) -> Unit = {},
    ): ModelCheckResult = withContext(Dispatchers.IO) {
        val file = fileFor(spec)
        markerFile(spec).delete()
        if (file.length() != spec.sizeBytes) {
            return@withContext ModelCheckResult.WrongSize(spec.sizeBytes, file.length())
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var hashed = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                hashed += read
                onProgress(hashed)
            }
        }
        if (digest.digest().toHex() != spec.sha256) return@withContext ModelCheckResult.WrongHash
        writeMarker(spec, file)
        ModelCheckResult.Ok
    }

    fun delete(spec: ModelSpec) {
        markerFile(spec).delete()
        fileFor(spec).delete()
        File(directory, spec.fileName + PARTIAL_SUFFIX).delete()
    }

    private fun markerFile(spec: ModelSpec) = File(directory, spec.fileName + MARKER_SUFFIX)

    private fun readMarker(spec: ModelSpec): Marker? = runCatching {
        json.decodeFromString(Marker.serializer(), markerFile(spec).readText())
    }.getOrNull()

    private fun writeMarker(spec: ModelSpec, file: File) {
        val marker = Marker(spec.sha256, file.length(), file.lastModified())
        markerFile(spec).writeText(json.encodeToString(Marker.serializer(), marker))
    }

    @Serializable
    private data class Marker(val sha256: String, val sizeBytes: Long, val lastModified: Long)

    private companion object {
        const val BUFFER_BYTES = 1 shl 20
        const val SPACE_MARGIN_BYTES = 512L * 1024 * 1024
        const val PARTIAL_SUFFIX = ".part"
        const val MARKER_SUFFIX = ".verified"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private const val HEX_DIGITS = "0123456789abcdef"

internal fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xff
        out.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0f])
    }
    return out.toString()
}
