package xyz.devnerd.anmediaplayer.ui.screens.player

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext

private const val CAST_SUB_TRACK_ID = 1L

/**
 * App-wide [CastPlayer]. Outlives individual [PlayerScreen]s (each episode is a fresh
 * screen) and is never released — `CastPlayer.release()` ends the cast session.
 */
@UnstableApi
internal object CastHolder {
    private var player: CastPlayer? = null
    private var tried = false

    /** Null when Play Services Cast is unavailable. */
    fun player(context: Context): CastPlayer? {
        if (!tried) {
            tried = true
            player = runCatching {
                CastPlayer(CastContext.getSharedInstance(context.applicationContext), SubtitleCastConverter())
            }.getOrNull()
        }
        return player
    }

    private fun session(context: Context) =
        runCatching { CastContext.getSharedInstance(context.applicationContext).sessionManager.currentCastSession }.getOrNull()

    fun deviceName(context: Context): String? = session(context)?.castDevice?.friendlyName

    /** True when the receiver went idle because the item played to the end. */
    fun finished(context: Context): Boolean =
        session(context)?.remoteMediaClient?.idleReason == MediaStatus.IDLE_REASON_FINISHED

    /** Toggle the sideloaded WebVTT track on the receiver. */
    fun setSubtitleOn(context: Context, on: Boolean) {
        session(context)?.remoteMediaClient?.setActiveMediaTracks(if (on) longArrayOf(CAST_SUB_TRACK_ID) else longArrayOf())
    }
}

/** Only http(s) streams are reachable by a TV — local / offline files can't be cast. */
internal fun isCastable(url: String): Boolean = url.startsWith("http://") || url.startsWith("https://")

/** Cast receivers need an explicit content type; guess it from the file extension. */
internal fun castMime(url: String): String = when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
    "mkv" -> MimeTypes.VIDEO_MATROSKA
    "webm" -> MimeTypes.VIDEO_WEBM
    "m3u8" -> MimeTypes.APPLICATION_M3U8
    "mpd" -> MimeTypes.APPLICATION_MPD
    "ts" -> MimeTypes.VIDEO_MP2T
    "mp3" -> MimeTypes.AUDIO_MPEG
    "m4a", "aac" -> MimeTypes.AUDIO_AAC
    "flac" -> MimeTypes.AUDIO_FLAC
    else -> MimeTypes.VIDEO_MP4
}

/**
 * Build the item sent to the receiver: explicit MIME type plus any http(s) WebVTT
 * subtitles (the Default Media Receiver can't render SRT or local/content URIs).
 */
internal fun castMediaItem(
    streamUrl: String,
    title: String,
    subtitle: String,
    subs: List<MediaItem.SubtitleConfiguration>,
    subtitleOn: Boolean,
): MediaItem {
    val vtt = subs.firstOrNull { it.mimeType == MimeTypes.TEXT_VTT && isCastable(it.uri.toString()) }
    return MediaItem.Builder()
        .setUri(streamUrl)
        .setMimeType(castMime(streamUrl))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).build())
        .apply {
            if (vtt != null) setSubtitleConfigurations(
                listOf(vtt.buildUpon().setSelectionFlags(if (subtitleOn) C.SELECTION_FLAG_DEFAULT else 0).build()),
            )
        }
        .build()
}

/** [DefaultMediaItemConverter] + WebVTT subtitle as a Cast [MediaTrack]. */
@UnstableApi
private class SubtitleCastConverter : MediaItemConverter {
    private val base = DefaultMediaItemConverter()

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = base.toMediaItem(mediaQueueItem)

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val queueItem = base.toMediaQueueItem(mediaItem)
        val sub = mediaItem.localConfiguration?.subtitleConfigurations?.firstOrNull() ?: return queueItem
        val info = queueItem.media ?: return queueItem
        val track = MediaTrack.Builder(CAST_SUB_TRACK_ID, MediaTrack.TYPE_TEXT)
            .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
            .setContentId(sub.uri.toString())
            .setContentType(MimeTypes.TEXT_VTT)
            .setLanguage(sub.language ?: "en")
            .setName(sub.label ?: "Subtitles")
            .build()
        val withTrack = MediaInfo.Builder(info.contentId)
            .setStreamType(info.streamType)
            .setContentType(info.contentType)
            .apply { info.contentUrl?.let { setContentUrl(it) } }
            .setMetadata(info.metadata)
            .setCustomData(info.customData)
            .setMediaTracks(listOf(track))
            .build()
        val on = (sub.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
        return MediaQueueItem.Builder(withTrack)
            .apply { if (on) setActiveTrackIds(longArrayOf(CAST_SUB_TRACK_ID)) }
            .build()
    }
}
