package com.issaczerubbabel.ledgar.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector

/** A feature shown on the More tab. Add one entry here and More shows it; no other screen changes. */
data class MoreFeature(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String
)

object MoreFeatures {
    /** How many features get a tile on More; the rest sit behind "All features". */
    const val MAX_TILES = 4

    val all: List<MoreFeature> = listOf(
        MoreFeature(
            id = "trips",
            title = "Trips",
            subtitle = "Split costs, settle up",
            icon = Icons.Filled.Luggage,
            route = Screen.Trips.route
        ),
        MoreFeature(
            id = "auto_capture",
            title = "Auto-capture",
            subtitle = "Bank and UPI alerts",
            icon = Icons.Filled.Notifications,
            route = Screen.CaptureSettings.route
        )
    )

    val tiles: List<MoreFeature> get() = all.take(MAX_TILES)
    val overflow: List<MoreFeature> get() = all.drop(MAX_TILES)
}
