package com.example.localupload

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.localupload.ui.PhotoDetailScreen
import com.example.localupload.ui.PhotoHistoryScreen
import com.example.localupload.ui.PhotoPickerScreen
import com.example.localupload.ui.UploadScreen
import com.example.localupload.ui.theme.LocalUploadTheme
import com.example.localupload.viewmodel.LocalUploadViewModel

private object Routes {
    const val GALLERY = "gallery"
    const val UPLOAD = "upload"
    const val HISTORY = "history"
    const val DETAIL = "detail/{photoId}"

    fun detail(photoId: String): String {
        return "detail/$photoId"
    }
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // WorkManager still runs if notification permission is denied.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            LocalUploadTheme {
                AppNavigation()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val navController = rememberNavController()

    /*
     * Shared across Gallery, Upload, History and Detail screens.
     * This allows prepared Picsum URIs to appear on UploadScreen.
     */
    val viewModel: LocalUploadViewModel = viewModel()

    val isPreparingPicsumPhotos by
    viewModel.isPreparingPicsumPhotos.collectAsState()

    val tabs = listOf(
        BottomTab(
            route = Routes.GALLERY,
            label = "Gallery",
            icon = Icons.Filled.PhotoLibrary
        ),
        BottomTab(
            route = Routes.UPLOAD,
            label = "Upload",
            icon = Icons.Filled.CloudUpload
        ),
        BottomTab(
            route = Routes.HISTORY,
            label = "History",
            icon = Icons.Filled.History
        )
    )

    val navBackStackEntry by
    navController.currentBackStackEntryAsState()

    val currentDestination =
        navBackStackEntry?.destination

    val showBottomBar =
        currentDestination?.route != Routes.DETAIL

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected =
                            currentDestination
                                ?.hierarchy
                                ?.any { destination ->
                                    destination.route == tab.route
                                } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(
                                        navController.graph
                                            .findStartDestination()
                                            .id
                                    ) {
                                        saveState = true
                                    }

                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.GALLERY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.GALLERY) {
                PhotoPickerScreen(
                    viewModel = viewModel,
                    isPreparingPhotos = isPreparingPicsumPhotos,
                    onPhotoClick = { photoId ->
                        navController.navigate(
                            Routes.detail(photoId)
                        )
                    },
                    onContinueToUpload = { selectedPhotos ->
                        /*
                         * Cache hit: reuse existing picsum_<id>.jpg.
                         * Cache miss: download it once.
                         * This does not start the server upload.
                         */
                        viewModel.preparePicsumPhotos(
                            photos = selectedPhotos,
                            onReady = {
                                navController.navigate(Routes.UPLOAD) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                )
            }

            composable(Routes.UPLOAD) {
                UploadScreen(
                    viewModel = viewModel
                )
            }

            composable(Routes.HISTORY) {
                PhotoHistoryScreen(
                    viewModel = viewModel
                )
            }

            composable(Routes.DETAIL) { backStackEntry ->
                val photoId =
                    backStackEntry.arguments
                        ?.getString("photoId")
                        .orEmpty()

                PhotoDetailScreen(
                    photoId = photoId,
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}