package com.college.bustracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = CollegePrimary,
    onPrimary = CollegeOnPrimary,
    primaryContainer = CollegePrimaryContainer,
    surface = CollegeSurface,
    onSurface = CollegeOnSurface,
    surfaceVariant = CollegeSurfaceVariant,
    onSurfaceVariant = CollegeOnSurfaceVariant
)

@Composable
fun CollegeBusTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
