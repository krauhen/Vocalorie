package com.example.vocalorie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.vocalorie.ui.SpikeScreen
import com.example.vocalorie.ui.VocalorieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VocalorieApp() }
    }
}

@Composable
fun VocalorieApp() {
    VocalorieTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            SpikeScreen()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VocalorieAppPreview() {
    VocalorieApp()
}
