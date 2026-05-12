package com.anticyscam.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.anticyscam.app.R

/**
 * Bottom navigation destinations. Order matters — the layout left-to-right
 * matches the order declared here.
 *
 * Route strings are also the [androidx.navigation.NavController] keys.
 */
enum class NavRoute(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    MainFunction("main_function", R.string.nav_main, Icons.Filled.GppGood),
    ScamInfo("scam_info", R.string.nav_scam_info, Icons.Filled.WarningAmber),
    Setting("setting", R.string.nav_setting, Icons.Filled.Settings)
}
