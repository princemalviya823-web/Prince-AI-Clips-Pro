
package com.prince.aiclips

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
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
                    status = if (uri != null) "Video selected" else "No video selected"
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

                                val duration = retriever
                                    .extractMetadata(
                                        MediaMetadataRetriever.METADATA_KEY_DURATION
                                    )?.toLongOrNull() ?: 0L

                                retriever.release()

                                if (duration <= 0L) {
                                    status = "Could not read video duration"
                                    return@Button
                                }

                                val clipDuration = minOf(duration, 30_000L)

                                val outputDir =
                                    context.getExternalFilesDir(
                                        Environment.DIRECTORY_MOVIES
                                    ) ?: context.cacheDir

                                val outputFile = File(
                                    outputDir,
                                    "PrinceClip_${System.currentTimeMillis()}.mp4"
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
                                status = "Creating your 30-second clip..."

                                val transformer = Transformer.Builder(context)
                                    .addListener(
                                        object : Transformer.Listener {
                                            override fun onCompleted(
                                                composition: Composition,
                                                result: ExportResult
                                            ) {
                                                isProcessing = false
                                                status = "Clip created successfully!"
                                                Toast.makeText(
                                                    context,
                                                    "Saved: ${outputFile.name}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }

                                            override fun onError(
                                                composition: Composition,
                                                result: ExportResult,
                                                exception: ExportException
                                            ) {
                                                isProcessing = false
                                                status = "Error: ${exception.message}"
                                            }
                                        }
                                    )
                                    .build()

                                transformer.start(mediaItem, outputFile.absolutePath)

                            } catch (e: Exception) {
                                isProcessing = false
                                status = "Error: ${e.message}"
                            }
                        },
                        enabled = selectedVideo != null && !isProcessing
                    ) {
                        Text(if (isProcessing) "Creating..." else "Create Clips")
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Output: First 30 seconds of your selected video")
                }
            }
        }
    }
}
