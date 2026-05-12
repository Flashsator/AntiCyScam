package com.anticyscam.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anticyscam.app.ui.bind.BindAppsScreen
import com.anticyscam.app.ui.mainfunction.MainFunctionScreen
import com.anticyscam.app.ui.scaminfo.ScamInfoScreen
import com.anticyscam.app.ui.setting.SettingScreen
import com.anticyscam.app.ui.theme.SurfaceBlack

/**
 * Top-level scaffold shown after the accessibility gate has been passed.
 *
 * Three bottom-tab routes (`main_function` / `scam_info` / `setting`) +
 * one modal sub-route (`bind_apps`). The bottom bar is hidden when the
 * user is on the bind-apps screen so that whole-screen takeover feels
 * intentional.
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != ROUTE_BIND_APPS

    Scaffold(
        containerColor = SurfaceBlack,
        bottomBar = { if (showBottomBar) BottomNavBar(navController) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.MainFunction.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SurfaceBlack)
        ) {
            composable(NavRoute.MainFunction.route) {
                MainFunctionScreen(
                    onOpenBindApps = { navController.navigate(ROUTE_BIND_APPS) }
                )
            }
            composable(NavRoute.ScamInfo.route) { ScamInfoScreen() }
            composable(NavRoute.Setting.route) { SettingScreen() }
            composable(ROUTE_BIND_APPS) {
                BindAppsScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}

const val ROUTE_BIND_APPS = "bind_apps"
