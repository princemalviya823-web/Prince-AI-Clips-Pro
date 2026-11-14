
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

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    private fun saveToGallery(source: File): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    "PrinceClip_${System.currentTimeMillis()}.mp4"
                )
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Prince AI Clips Pro"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw Exception("Could not create Gallery file")

            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not save video")

                val finished = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, finished, null, null)

                return "Saved in Gallery: Movies/Prince AI Clips Pro"
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val folder = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: throw Exception("Storage unavailable")

            val destination = File(
                folder,
                "PrinceClip_${System.currentTimeMillis()}.mp4"
            )
            source.copyTo(destination, overwrite = true)
            return "Saved in app storage: ${destination.name}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                var selectedVideo by remember { mutableStateOf<Uri?>(null) }
                var status by remember { mutableStateOf("Select a video") }
                var isProcessing by remember { mutableStateOf(false) }

                val videoPicker = rememberLauncherForActivityResult(
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
                        onClick = { videoPicker.launch("video/*") },
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

                                if (duration <= 0L) {
                                    status = "Could not read video duration"
                                    return@Button
                                }

                                val clipDuration = minOf(duration, 30_000L)
                                val outputFile = File(
                                    cacheDir,
                                    "PrinceTemp_${System.currentTimeMillis()}.mp4"
                                )

                                val mediaItem = MediaItem.Builder()
                                    .setUri(uri)
                                    .setClippingConfiguration(
                                        MediaItem.ClippingConfiguration.Builder()
                                            .setStartPositionMs(0)
                                            .setEndPositionMs(clipDuration)
                                            .build()
                                    )
                                    .build()

                                isProcessing = true
                                status = "Creating your clip..."

                                val transformer = Transformer.Builder(context)
                                    .addListener(
                                        object : Transformer.Listener {
                                            override fun onCompleted(
                                                composition: Composition,
                                                result: ExportResult
                                            ) {
                                                try {
                                                    val message =
                                                        this@MainActivity.saveToGallery(
                                                            outputFile
                                                        )
                                                    status = message
                                                } catch (e: Exception) {
                                                    status = "Save error: ${e.message}"
                                                } finally {
                                                    outputFile.delete()
                                                    isProcessing = false
                                                }
                                            }

                                            override fun onError(
                                                composition: Composition,
                                                result: ExportResult,
                                                exception: ExportException
                                            ) {
                                                outputFile.delete()
                                                isProcessing = false
                                                status = "Export error: ${exception.message}"
                                            }
                                        }
                                    )
                                    .build()

                                transformer.start(
                                    mediaItem,
                                    outputFile.absolutePath
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

