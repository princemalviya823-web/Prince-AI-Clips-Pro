

package com.prince.aiclips

import android.content.ContentValues
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

@UnstableApi
class MainActivity : ComponentActivity() {

    private fun saveToGallery(file: File, number: Int) {
        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "PrinceClip_${number}.mp4"
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
        ) ?: throw Exception("Gallery file creation failed")

        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not save video")

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

    @OptIn(UnstableApi::class)
    private fun createClips(
        uri: Uri,
        duration: Long,
        count: Int,
        progress: (String) -> Unit,
        finished: (String) -> Unit
    ) {
        val clipLength = minOf(60_000L, duration / count)
        var index = 0

        lateinit var startNext: () -> Unit

        startNext = {
            if (index >= count) {
                finished("$count clips saved in Gallery!")
            } else {
                val currentIndex = index
                val startMs = currentIndex * clipLength
                val endMs = startMs + clipLength

                val output = File(
                    cacheDir,
                    "PrinceTemp_${System.currentTimeMillis()}_$currentIndex.mp4"
                )

                progress("Creating clip ${currentIndex + 1} of $count...")

                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build()
                    )
                    .build()

                val transformer = Transformer.Builder(this)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                result: ExportResult
                            ) {
                                try {
                                    saveToGallery(output, currentIndex + 1)
                                    output.delete()
                                    index++
                                    startNext()
                                } catch (e: Exception) {
                                    output.delete()
                                    finished("Save error: ${e.message}")
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
                        }
                    )
                    .build()

                transformer.start(mediaItem, output.absolutePath)
            }
        }

        startNext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                var selectedVideo by remember { mutableStateOf<Uri?>(null) }
                var status by remember { mutableStateOf("Select a video") }
                var isProcessing by remember { mutableStateOf(false) }

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

                            try {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(context, uri)

                                val duration = retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_DURATION
                                )?.toLongOrNull() ?: 0L

                                retriever.release()

                                if (duration < 40_000L) {
                                    status = "Video must be at least 40 seconds"
                                    return@Button
                                }

                                val count = minOf(
                                    23,
                                    (duration / 40_000L).toInt()
                                )

                                isProcessing = true

                                createClips(
                                    uri = uri,
                                    duration = duration,
                                    count = count,
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

                            } catch (e: Exception) {
                                isProcessing = false
                                status = "Error: ${e.message}"
                            }
                        },
                        enabled = selectedVideo != null && !isProcessing
                    ) {
                        Text(
                            if (isProcessing) "Creating..."
                            else "Create Clips"
                        )
                    }
                }
            }
        }
    }
}

