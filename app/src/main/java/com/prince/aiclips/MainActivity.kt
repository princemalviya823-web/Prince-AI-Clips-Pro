package com.prince.aiclips

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var videoLink by remember { mutableStateOf("") }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text("Prince AI Clips Pro", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp))
                    Text("Create your short video clips")
                    OutlinedTextField(
                        value = videoLink,
                        onValueChange = { videoLink = it },
                        label = { Text("Enter video link") }
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { }) {
                        Text("Create Clips")
                    }
                    Text("Use videos you own or have permission to edit.")
                }
            }
        }
    }
}
