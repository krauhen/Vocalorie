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
    val container = remember(context) { AppContainer.get(context) }
    // Seeded on the caller's thread so the very first frame is painted with the saved palette
    // rather than the defaults; it follows the selected tab from here on.
    var activeThemeColors by remember {
        mutableStateOf(container.themeSettingsRepository.currentSnapshot().mealColors)
    }

    VocalorieTheme(themeColors = activeThemeColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            MealCaptureScreen(onActiveThemeColorsChange = { activeThemeColors = it })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VocalorieAppPreview() {
    VocalorieApp()
}
