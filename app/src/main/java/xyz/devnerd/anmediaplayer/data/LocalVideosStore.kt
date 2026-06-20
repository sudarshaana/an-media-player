package xyz.devnerd.anmediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A video MediaStore knows about, anywhere on the device — not necessarily ours. */
data class LocalVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val path: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateModified: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int,
)

sealed class DeleteAction {
    object Deleted : DeleteAction()
    /** Caller must launch this via an IntentSender result launcher for the user to confirm. */
    data class NeedsConsent(val intentSender: android.content.IntentSender) : DeleteAction()
    object Failed : DeleteAction()
}

/** Whole-device video scan via MediaStore (Local Files tab) — read-only listing + delete. */
object LocalVideosStore {

    suspend fun scan(context: Context): List<LocalVideo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalVideo>()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        runCatching {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val wCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    list.add(
                        LocalVideo(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            displayName = c.getString(nameCol) ?: "Video",
                            path = c.getString(pathCol) ?: "",
                            sizeBytes = c.getLong(sizeCol),
                            durationMs = c.getLong(durCol),
                            dateModified = c.getLong(dateCol) * 1000,
                            mimeType = c.getString(mimeCol),
                            width = c.getInt(wCol),
                            height = c.getInt(hCol),
                        ),
                    )
                }
            }
        }
        list
    }

    /** API 30+: always needs a system consent dialog for media the app doesn't own.
     * API 29: try direct delete, recover via RecoverableSecurityException if denied.
     * <29: legacy storage — delete the file directly, then drop the MediaStore row. */
    fun delete(context: Context, video: LocalVideo): DeleteAction {
        if (Build.VERSION.SDK_INT >= 30) {
            return DeleteAction.NeedsConsent(MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri)).intentSender)
        }
        return try {
            if (Build.VERSION.SDK_INT < 29 && video.path.isNotBlank()) java.io.File(video.path).delete()
            context.contentResolver.delete(video.uri, null, null)
            DeleteAction.Deleted
        } catch (e: SecurityException) {
            val recoverable = e as? android.app.RecoverableSecurityException
            if (recoverable != null) DeleteAction.NeedsConsent(recoverable.userAction.actionIntent.intentSender) else DeleteAction.Failed
        } catch (e: Exception) {
            DeleteAction.Failed
        }
    }

    /** Video codec ("AVC", "HEVC", ...), read lazily for the details sheet — costlier than the list columns. */
    fun videoCodec(context: Context, uri: Uri): String? = runCatching {
        val ex = android.media.MediaExtractor()
        ex.setDataSource(context, uri, null)
        var codec: String? = null
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) { codec = mime.removePrefix("video/").uppercase(); break }
        }
        ex.release()
        codec
    }.getOrNull()
}
