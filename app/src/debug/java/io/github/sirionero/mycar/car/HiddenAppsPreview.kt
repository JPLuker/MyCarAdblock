package io.github.sirionero.mycar.car

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarCarTheme

/**
 * Debug only, for CarPreviewActivity's hiddenApps mode: the car settings' hidden apps on their own,
 * as the settings screen scrolls them out of a screenshot.
 */
@Composable
internal fun HiddenAppsPreview() {
    MyCarCarTheme(dark = true) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
            HiddenAppsSettings()
        }
    }
}
