package com.example.vocalorie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.vocalorie.settings.ThemeSettingsStore
import com.example.vocalorie.ui.MealCaptureScreen
import com.example.vocalorie.ui.VocalorieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VocalorieApp() }
    }
}

@Composable
fun VocalorieApp() {
    val context = LocalContext.current
    val themeSettingsStore = remember { ThemeSettingsStore(context) }
    var activeThemeColors by remember { mutableStateOf(themeSettingsStore.get()) }

    VocalorieTheme(themeColors = activeThemeColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            MealCaptureScreen(
                activeThemeColors = activeThemeColors,
                onActiveThemeColorsChange = { activeThemeColors = it },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VocalorieAppPreview() {
    VocalorieApp()
}
