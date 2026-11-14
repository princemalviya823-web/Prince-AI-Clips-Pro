

package com.prince.aiclips

import android.net.Uri
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                var selectedVideo by remember { mutableStateOf<Uri?>(null) }

                val videoPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    selectedVideo = uri
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

                    Text("Select a video to get started")

                    Spacer(Modifier.height(16.dp))

                    Button(onClick = {
                        videoPicker.launch("video/*")
                    }) {
                        Text("Select Video")
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        if (selectedVideo != null)
                            "Video selected successfully!"
                        else
                            "No video selected"
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            Toast.makeText(
                                context,
                                "Clip creation feature is not added yet.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        enabled = selectedVideo != null
                    ) {
                        Text("Create Clips")
                    }
                }
            }
        }
    }
}
