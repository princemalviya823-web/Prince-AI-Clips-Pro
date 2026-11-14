

package com.prince.aiclips

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

data class ClipCandidate(
    val start: Long,
    val end: Long,
    val score: Double
)

@UnstableApi
class MainActivity : ComponentActivity() {

    private fun saveToGallery(file: File, number: Int) {
        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "PrinceClip_${System.currentTimeMillis()}_$number.mp4"
            )
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Prince AI Clips Pro"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw Exception("Gallery save failed")

        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Cannot open Gallery file")

            if (Build.VERSION.SDK_INT >= 29) {
                val update = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, update, null, null)
            }
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun analyzeVideo(
        uri: Uri,
        duration: Long
    ): List<ClipCandidate> {

        val retriever = MediaMetadataRetriever()
        val candidates = mutableListOf<ClipCandidate>()

        try {
            retriever.setDataSource(this, uri)

            val chunk = 40_000L
            val total = (duration / chunk).toInt()

            for (index in 0 until total) {
                val start = index * chunk
                val end = start + chunk
                var previous: Bitmap? = null
                var score = 0.0
                var samples = 0

                var time = start + 2_000L

                while (time < end) {
                    val frame = retriever.getFrameAtTime(
                        time * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )

                    if (frame != null) {
                        val small = Bitmap.createScaledBitmap(
                            frame, 24, 24, true
                        )

                        if (small !== frame) frame.recycle()

                        val old = previous

                        if (old != null) {
                            var difference = 0.0

                            for (y in 0 until 24) {
                                for (x in 0 until 24) {
                                    val a = old.getPixel(x, y)
                                    val b = small.getPixel(x, y)

                                    val grayA =
                                        (android.graphics.Color.red(a) +
                                        android.graphics.Color.green(a) +
                                        android.graphics.Color.blue(a)) / 3

                                    val grayB =
                                        (android.graphics.Color.red(b) +
                                        android.graphics.Color.green(b) +
                                        android.graphics.Color.blue(b)) / 3

                                    difference += kotlin.math.abs(
                                        grayA - grayB
                                    )
                                }
                            }

                            score += difference / (24.0 * 24.0)
                            samples++
                            old.recycle()
                        }

                        previous = small
                    }

                    time += 2_000L
                }

                previous?.recycle()

                candidates.add(
                    ClipCandidate(
                        start = start,
                        end = end,
                        score = if (samples > 0) score / samples else 0.0
                    )
                )
            }
        } finally {
            retriever.release()
        }

        val desired = min(
            23,
            ceil(duration / 60_000.0).toInt()
        )

        return candidates
            .sortedByDescending { it.score }
            .take(desired)
            .sortedBy { it.start }
    }

    @OptIn(UnstableApi::class)
    private fun createClips(
        uri: Uri,
        candidates: List<ClipCandidate>,
        progress: (String) -> Unit,
        finished: (String) -> Unit
    ) {
        var index = 0

        fun startNext() {
            if (index >= candidates.size) {
                finished("Done! ${candidates.size} clips saved in Gallery.")
                return
            }

            val candidate = candidates[index]
            val number = index + 1
            val output = File(
                cacheDir,
                "prince_clip_$number.mp4"
            )

            progress("Creating clip $number of ${candidates.size}")

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(candidate.start)
                        .setEndPositionMs(candidate.end)
                        .build()
                )
                .build()

            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {

                    override fun onCompleted(
                        composition: Composition,
                        result: ExportResult
                    ) {
                        try {
                            saveToGallery(output, number)
                            output.delete()
                            index++
                            startNext()
                        } catch (e: Exception) {
                            output.delete()
                            finished("Gallery error: ${e.message}")
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        output.delete()
                        finished("Export error: ${exception.message}")
                    }
                })
                .build()

            try {
                transformer.start(mediaItem, output.absolutePath)
            } catch (e: Exception) {
                output.delete()
                finished("Error: ${e.message}")
            }
        }

        startNext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                var selectedVideo by remember {
                    mutableStateOf<Uri?>(null)
                }
                var status by remember {
                    mutableStateOf("Select a video")
                }
                var isProcessing by remember {
                    mutableStateOf(false)
                }

                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    selectedVideo = uri
                    status = if (uri != null) {
                        "Video selected"
                    } else {
                        "No video selected"
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        "Prince AI Clips Pro",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { picker.launch("video/*") },
                        enabled = !isProcessing
                    ) {
                        Text("Select Video")
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(status)
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val uri = selectedVideo ?: return@Button

                            isProcessing = true
                            status = "Analyzing video movement..."

                            Thread {
                                try {
                                    val retriever =
                                        MediaMetadataRetriever()

                                    retriever.setDataSource(context, uri)

                                    val duration =
                                        retriever.extractMetadata(
                                            MediaMetadataRetriever
                                                .METADATA_KEY_DURATION
                                        )?.toLongOrNull() ?: 0L

                                    retriever.release()

                                    if (duration < 40_000L) {
                                        runOnUiThread {
                                            isProcessing = false
                                            status =
                                                "Video must be at least 40 seconds"
                                        }
                                        return@Thread
                                    }

                                    val candidates =
                                        analyzeVideo(uri, duration)

                                    runOnUiThread {
                                        if (candidates.isEmpty()) {
                                            isProcessing = false
                                            status = "No clips found"
                                        } else {
                                            status =
                                                "Found ${candidates.size} highlight clips"

                                            createClips(
                                                uri = uri,
                                                candidates = candidates,
                                                progress = { message ->
                                                    runOnUiThread {
                                                        status = message
                                                    }
                                                },
                                                finished = { message ->
                                                    runOnUiThread {
                                                        isProcessing = false
                                                        status = message
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        isProcessing = false
                                        status = "Error: ${e.message}"
                                    }
                                }
                            }.start()
                        },
                        enabled = selectedVideo != null && !isProcessing
                    ) {
                        Text(
                            if (isProcessing) "Processing..."
                            else "Find Best Moments"
                        )
                    }
                }
            }
        }
    }
}

