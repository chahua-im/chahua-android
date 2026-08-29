package net.paigu.chahua.ui.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EncoderSelector
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/** 视频源尺寸/帧率信息（来自 MediaMetadataRetriever）。 */
internal data class VideoSourceInfo(
    val width: Int,
    val height: Int,
    val frameRate: Int,
)

/**
 * 视频压缩：把输入 Uri 转码为 1080p/10Mbps H.264 + AAC 并写入缓存目录。
 * 压缩完成后通过 [callbackScope] 回调结果。
 */
object VideoCompressor {
    fun compress(
        context: Context,
        uriString: String,
        callbackScope: CoroutineScope,
        onReady: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        runCatching {
            val uri = Uri.parse(uriString)
            val videoInfo = queryVideoInfo(context, uri)
            val frameRate = (videoInfo?.frameRate ?: 30).coerceIn(1, 60)
            val presentation = if (videoInfo != null) {
                val scale = minOf(1.0, 1920.0 / maxOf(videoInfo.width, videoInfo.height))
                val outputWidth = (videoInfo.width * scale).roundToInt().coerceAtLeast(2)
                val outputHeight = (videoInfo.height * scale).roundToInt().coerceAtLeast(2)
                Presentation.createForWidthAndHeight(
                    outputWidth,
                    outputHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT,
                )
            } else {
                Presentation.createForShortSide(1080)
            }
            val effects = Effects(
                emptyList<AudioProcessor>(),
                listOf<Effect>(presentation),
            )
            val videoSettings = VideoEncoderSettings.Builder()
                .setBitrate(10 * 1000 * 1000)
                .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                .setiFrameIntervalSeconds(1f)
                .setMaxBFrames(0)
                .build()
            val audioSettings = AudioEncoderSettings.Builder()
                .setBitrate(128 * 1024)
                .build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setVideoEncoderSelector(EncoderSelector.DEFAULT)
                .setRequestedVideoEncoderSettings(videoSettings)
                .setRequestedAudioEncoderSettings(audioSettings)
                .setEnableFallback(true)
                .build()
            val outputDir = File(context.cacheDir, "compressed_videos").apply { mkdirs() }
            val output = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4")
            val transformer = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        callbackScope.launch {
                            onReady(Uri.fromFile(output).toString())
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exception: ExportException,
                    ) {
                        callbackScope.launch {
                            onError(exception.message ?: "compress failed")
                        }
                    }
                })
                .build()
            transformer.start(
                EditedMediaItem.Builder(MediaItem.fromUri(uriString))
                    .setFrameRate(frameRate)
                    .setEffects(effects)
                    .build(),
                output.absolutePath,
            )
        }.onFailure {
            onError(it.message ?: "compress failed")
        }
    }

    private fun queryVideoInfo(context: Context, uri: Uri): VideoSourceInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            var width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: return null
            var height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: return null
            if (width <= 0 || height <= 0) return null
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }
            val frameRate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
                ?.roundToInt()
                ?: 30
            VideoSourceInfo(width = width, height = height, frameRate = frameRate)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
