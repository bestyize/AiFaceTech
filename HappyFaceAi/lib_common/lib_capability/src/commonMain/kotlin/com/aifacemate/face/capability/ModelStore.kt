package com.aifacemate.face.capability

import kotlin.math.roundToInt

/** A pinned, downloadable model file. [directory] is relative to the platform model root. */
data class ModelSpec(
    val id: String,
    val directory: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
)

/** Downloads, verifies and deletes managed model files. It never touches user-provided custom models. */
interface ModelStore {
    val supportsDownloads: Boolean

    /** Cheap check: the file exists with the pinned size. [verifiedPath] also checks the SHA-256. */
    fun installed(model: ModelSpec): Boolean

    /** Downloads missing files. Progress covers all [models]; failure or cancellation leaves no partial file. */
    suspend fun install(models: List<ModelSpec>, onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> })

    suspend fun remove(models: List<ModelSpec>)

    /** Absolute path of a verified file for native runtimes; throws if it is missing or corrupt. */
    suspend fun verifiedPath(model: ModelSpec): String
}

expect fun platformModelStore(): ModelStore

/** "约 5.0 MB" below 10 MB, "约 82 MB" above. Decimal megabytes, matching download sizes. */
fun formatModelSize(bytes: Long): String {
    val megabytes = bytes / 1_000_000.0
    return if (megabytes < 10) "约 ${(megabytes * 10).roundToInt() / 10.0} MB" else "约 ${megabytes.roundToInt()} MB"
}
